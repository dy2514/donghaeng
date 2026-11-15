package ai.pleos.coach

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.pleos.coach.audio.*
import ai.pleos.coach.core.*
import ai.pleos.coach.data.*
import ai.pleos.coach.platform.*
import ai.pleos.coach.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import ai.pleos.coach.core.Difficulty as CoreDifficulty
import ai.pleos.coach.ui.Difficulty as UiDifficulty

class CoachViewModel(application:Application):AndroidViewModel(application) {
 private val store=LocalStore(application)
 private var saved=runCatching{store.load()?.let(StateCodec::decode)}.getOrNull()?:SavedState()
 private val automotive=application.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
 private var parkedAllowed=!automotive
 private var hostStarted=false
 private var engine:CoachEngine?=null
 private var replayJob:Job?=null
 private var lastTimestamp=0L
 private var lastLive:Snapshot?=null
 private var currentAudioRecord:String?=null
 private var activeSegment:String?=null
 private var liveConfig:LiveRouteConfig?=null
 private var connectionState=emptyList<ConnectionStatus>()
 var requestRouteImport:(()->Unit)?=null
 var uiState by mutableStateOf(CoachUiState(editingAllowed=!automotive))
  private set

 private val audio=GuidanceAudio(application){event->
  val label=when(event.stage){
   AudioStage.STARTED->"음성 재생 시작"
   AudioStage.COMPLETED->"음성 재생 완료"
   AudioStage.FOCUS_DENIED->"오디오 포커스 거부 · 음성 생략"
   AudioStage.INTERRUPTED->"음성 중단 · 뒤늦게 재생하지 않음"
   AudioStage.UNAVAILABLE->"음성 사용 불가"
  }
  currentAudioRecord?.let { id->saved=saved.copy(audioOutcomes=saved.audioOutcomes+(id to label)) }
  uiState=uiState.copy(audioStatus=label)
  persist()
 }
 private val live=LiveTelemetry(application,
  onSnapshot={snapshot->
   lastLive=snapshot
   if(uiState.mode==InputMode.LIVE){
    if(uiState.recording){
     consume(snapshot)
     if(!snapshot.routeActive)finishSession("경로가 취소되거나 바뀌어 기록을 마쳤어요.")
    }else uiState=uiState.copy(speedKmh=snapshot.speedKmh?.toFloat(),brake=snapshot.brake,signal=snapshot.signal.name)
   }
   refresh()
  },
  onStatus={connectionState=it;refresh()},
  onEditingAllowed={allowed->
   parkedAllowed=if(automotive)allowed else true
   if(!parkedAllowed && uiState.mode==InputMode.REPLAY && uiState.recording)finishSession("주차 상태가 해제되어 재생을 종료했어요.")
   refresh()
  })

 val actions=CoachActions(
  onScreen={screen->
   if(!uiState.recording && parkedAllowed && screen!=CoachScreen.DRIVING)uiState=uiState.copy(screen=screen,statusMessage=null)
  },
  onMode={mode->if(!uiState.recording&&parkedAllowed){
   uiState=uiState.copy(mode=mode,statusMessage=null,speedKmh=null,brake=null,signal=null);refresh()
  }},
  onScenario={id->if(!uiState.recording&&parkedAllowed){uiState=uiState.copy(selectedScenarioId=id);refresh()}},
  onStart={startSession()},
  onPause={if(uiState.mode==InputMode.REPLAY&&uiState.recording){
   audio.stop();uiState=uiState.copy(replayPaused=!uiState.replayPaused)
  }},
  onStop={if(uiState.mode==InputMode.REPLAY||parkedAllowed)finishSession()},
  onSelectSegment={id->if(!uiState.recording&&parkedAllowed)uiState=uiState.copy(selectedSegmentId=id)},
  onSaveSetting={setting->saveSetting(setting)},
  onConnect={if(!uiState.recording&&parkedAllowed){live.stop();live.configureRoute(liveConfig);live.start()}},
  onImportRoute={if(!uiState.recording&&parkedAllowed)requestRouteImport?.invoke()}
 )

 init {
  store.loadRoute()?.let {raw->runCatching{parseRoute(raw)}.onSuccess{liveConfig=it;live.configureRoute(it)} }
  uiState=uiState.copy(statusMessage=store.warning,selectedScenarioId=if(saved.records.any{it.source==Source.REPLAY})"PRACTICE"else "FIRST")
  refresh()
 }
 fun onPermissionsChanged(){ if(hostStarted && !uiState.recording){ live.stop();live.configureRoute(liveConfig);live.start() } }
 fun onHostStart(){
  if(hostStarted)return
  hostStarted=true;live.configureRoute(liveConfig);live.start()
 }
 fun onHostStop(){
  if(!hostStarted)return
  hostStarted=false
  if(uiState.recording)finishSession("앱이 화면에서 벗어나 현재 기록을 저장했어요.")
  audio.stop();live.stop()
 }
 fun showMessage(message:String){uiState=uiState.copy(statusMessage=message)}
 private fun refresh(){
  val source=if(uiState.mode==InputMode.REPLAY)Source.REPLAY else Source.LIVE
  val records=saved.records.filter{it.source==source}.sortedByDescending{it.startedAtMs}
  val date=SimpleDateFormat("MM월 dd일 HH:mm:ss",Locale.KOREA)
  uiState=uiState.copy(
   editingAllowed=parkedAllowed,
   canStart=!uiState.recording&&parkedAllowed&&(source==Source.REPLAY||(liveConfig!=null&&lastLive?.routeActive==true&&lastLive?.locationReliable==true)),
   segments=SampleRoute.segments.map {s->
    val p=saved.preferences.find{it.segmentId==s.id}?:Preference(s.id)
    UiSegment(s.id,s.name,s.direction.name,if(s.order==0)"학교 앞 · 등록한 진입 방향"else "정문 앞 · 등록한 진입 방향",
     SegmentSetting(s.id,p.selected,DifficultyReason.valueOf(p.reason.name),LearningGoal.valueOf(p.goal.name),HelpLevel.valueOf(p.assistance.name),UiDifficulty.valueOf(p.difficulty.name),p.note),s.approachId)
   },
   records=records.map {r->
    val base=when(r.decision.reason){
     DecisionReason.PROMPTED->r.decision.prompt.orEmpty()
     DecisionReason.SIGNAL_ALREADY_ON->"판단 시점에 "+r.decision.observedSignal.label+" 방향지시등 상태가 확인됐어요."
     DecisionReason.NOT_SELECTED->"첫 기록 또는 연습 대상으로 선택하지 않은 구간이에요."
     DecisionReason.REVIEW_ONLY->"사용자가 주행 후 복습을 선택해 추가 안내를 하지 않았어요."
     DecisionReason.SPEED_REVIEW_ONLY->"속도와 페달은 주차 후 확인하는 목표예요."
     DecisionReason.DATA_UNAVAILABLE->"필요한 데이터가 없거나 오래되어 안내를 보류했어요."
     DecisionReason.ROUTE_CHANGED->"경로 변경 또는 취소로 남아 있던 안내를 지웠어요."
     else->"접근 방향과 경로 순서를 확인하지 못했어요."
    }
    val detail=base+(saved.audioOutcomes[r.id]?.let{"\n음성: "+it}?:"")
    UiRecord(r.id,r.segmentId,r.approachId,SampleRoute.segment(r.segmentId)?.name.orEmpty(),date.format(Date(r.startedAtMs)),r.source.label,
     r.samples.map{UiSample(it.elapsedMs/1000f,it.speedKmh?.toFloat(),it.brake,it.signal.name)},r.decision.reason.label,detail,r.durationMs/1000f,r.difficulty?.label,r.note)
   },
   scenarios=ReplayScenario.entries.map {scenario->UiScenario(scenario.name,scenario.label,when(scenario){
    ReplayScenario.FIRST->"개인 설정을 바꾸지 않고, 두 구간을 기록만 해요. 약 31초."
    ReplayScenario.PRACTICE->"저장한 목표와 도움 단계를 적용해요. B구간은 신호가 먼저 켜져 있어요."
    ReplayScenario.SIGNAL_READY->"두 구간에서 해당 방향 신호가 미리 켜져 있어요."
    ReplayScenario.MISSING_DATA->"A구간의 위치·속도·페달·신호 누락을 확인해요."
    ReplayScenario.ROUTE_CANCELLED->"A구간 도중 경로 취소 후 추가 안내가 없는지 확인해요."
    ReplayScenario.WRONG_APPROACH->"반대 접근 방향을 같은 연습 구간으로 인정하지 않아요."
   })},
   connections=connectionState.map{UiConnection(it.label,it.detail,when(it.state){ConnectionState.READY->true;ConnectionState.CONNECTING->null;else->false})},
   routeLabel=if(source==Source.REPLAY)"등교 연습 경로 · 예시" else "등록한 실제 연습 경로"
  )
 }
 private fun startSession(){
  if(!uiState.canStart)return
  val source=if(uiState.mode==InputMode.REPLAY)Source.REPLAY else Source.LIVE
  val now=System.currentTimeMillis()
  val scenario=runCatching{ReplayScenario.valueOf(uiState.selectedScenarioId)}.getOrDefault(ReplayScenario.FIRST)
  val preferences=if(source==Source.REPLAY&&scenario==ReplayScenario.FIRST)saved.preferences.map{it.copy(selected=false)}else saved.preferences
  engine=CoachEngine(preferences,source,UUID.randomUUID().toString(),now)
  activeSegment=null;lastTimestamp=now;currentAudioRecord=null
  uiState=uiState.copy(recording=true,replayPaused=false,screen=CoachScreen.DRIVING,statusMessage=null,progress=0f,speedKmh=null,brake=null,signal=null,activeSegmentId=null,
   drivingMessage=if(preferences.none{it.selected})"두 구간의 주행을 기록합니다"else "선택한 연습 설정으로 기록합니다",drivingDetail="기본 내비는 길 안내를, 동행은 나의 연습을 기록해요.",audioStatus="")
  refresh()
  if(source==Source.REPLAY){
   replayJob=viewModelScope.launch{
    val frames=ReplayFactory.frames(scenario,now)
    for((index,frame) in frames.withIndex()){
     while(uiState.replayPaused&&uiState.recording)delay(100)
     if(!uiState.recording)break
     consume(frame);uiState=uiState.copy(progress=index.toFloat()/(frames.size-1))
     delay(500)
    }
    if(uiState.recording)finishSession("주행 기록을 저장했습니다")
   }
  }
 }
 private fun consume(snapshot:Snapshot){
  val current=engine?:return
  lastTimestamp=snapshot.timestampMs
  if(snapshot.segmentId!=activeSegment||!snapshot.routeActive||!snapshot.locationReliable)audio.stop()
  activeSegment=snapshot.segmentId
  val update=current.ingest(snapshot)
  if(update.completedRecords.isNotEmpty()){saved=saved.copy(records=StateCodec.retainLatest(saved.records+update.completedRecords));persist()}
  val decision=update.decision
  if(decision!=null){
   val text=decision.prompt?:decision.reason.label
   uiState=uiState.copy(drivingMessage=text,drivingDetail=if(decision.shouldSpeak)"선택한 목표에 대한 교육 안내예요."else "관찰된 상태와 내가 선택한 도움 수준을 기록해요.")
   if(decision.shouldSpeak){
    currentAudioRecord=current.sessionId+"-"+decision.segmentId
    audio.play(decision.prompt!!)
   }
  }
  val fresh:(Long)->Boolean={it<=snapshot.timestampMs&&snapshot.timestampMs-it<=CoachEngine.MAX_DATA_AGE_MS}
  uiState=uiState.copy(activeSegmentId=update.activeSegment?.id,
   speedKmh=update.sample?.speedKmh?.toFloat()?:snapshot.speedKmh?.takeIf{fresh(snapshot.speedTimestampMs)&&it.isFinite()&&it>=0}?.toFloat(),
   brake=if(fresh(snapshot.brakeTimestampMs))snapshot.brake else null,
   signal=if(fresh(snapshot.signalTimestampMs))snapshot.signal.name else null)
 }
 private fun finishSession(message:String?=null){
  if(!uiState.recording)return
  replayJob?.cancel();replayJob=null;audio.stop()
  val finalRecords=engine?.finish(lastTimestamp).orEmpty()
  saved=saved.copy(records=StateCodec.retainLatest(saved.records+finalRecords))
  engine=null;activeSegment=null;currentAudioRecord=null;persist()
  uiState=uiState.copy(recording=false,replayPaused=false,screen=CoachScreen.REVIEW,activeSegmentId=null,statusMessage=message?:if(finalRecords.isEmpty())"유효한 접근 기록이 없어요. 입력과 경로를 확인해주세요."else "주행 기록을 저장했습니다",progress=1f)
  refresh()
 }
 private fun saveSetting(s:SegmentSetting){
  if(uiState.recording||!parkedAllowed)return
  val p=Preference(s.segmentId,s.selected,Reason.valueOf(s.reason.name),Goal.valueOf(s.goal.name),Assistance.valueOf(s.help.name),CoreDifficulty.valueOf(s.difficulty.name),s.note.take(120))
  val source=if(uiState.mode==InputMode.REPLAY)Source.REPLAY else Source.LIVE
  val latest=saved.records.filter{it.source==source&&it.segmentId==s.segmentId}.maxByOrNull{it.startedAtMs}?.id
  saved=saved.copy(preferences=saved.preferences.filterNot{it.segmentId==s.segmentId}+p,
   records=saved.records.map{if(it.id==latest)it.copy(difficulty=p.difficulty,reason=p.reason,note=p.note)else it})
  persist();uiState=uiState.copy(statusMessage="연습 설정을 저장했습니다",selectedScenarioId=if(uiState.selectedScenarioId=="FIRST")"PRACTICE"else uiState.selectedScenarioId)
  refresh()
 }
 private fun persist(){store.save(StateCodec.encode(saved))}
 fun importRoute(raw:String){
  if(uiState.recording||!parkedAllowed)return
  runCatching{parseRoute(raw)}.onSuccess{liveConfig=it;store.saveRoute(raw);live.configureRoute(it);showMessage("실제 연습 경로 설정을 저장했어요. 내비에서 같은 경로를 실행하세요.");refresh()}
   .onFailure{showMessage("경로 파일 형식을 확인해주세요: "+it.message.orEmpty())}
 }
 private fun parseRoute(raw:String):LiveRouteConfig{
  require(raw.length<65536){"경로 파일이 너무 큽니다."}
  val json=JSONObject(raw);require(json.optBoolean("calibrated",false)){"실제 측정 후 calibrated를 true로 설정하세요."}
  val list=json.getJSONArray("segments")
  return LiveRouteConfig(json.getString("routeId"),json.getString("nativeRouteId"),List(list.length()){i->val s=list.getJSONObject(i);LiveSegmentConfig(s.getString("segmentId"),s.getString("approachId"),s.getDouble("latitude"),s.getDouble("longitude"),s.getDouble("approachHeadingDegrees"),s.getInt("startRouteIndex"),s.getInt("endRouteIndex"),TurnSignal.valueOf(s.getString("turn")),s.optDouble("radiusMeters",160.0),s.optDouble("headingToleranceDegrees",30.0),s.optDouble("maxTbtDistanceMeters",200.0))})
 }
 override fun onCleared(){replayJob?.cancel();audio.release();live.stop();super.onCleared()}
}
