package ai.pleos.coach.platform
import ai.pleos.coach.core.*
import ai.pleos.playground.navi.constants.*
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlin.math.*

enum class ConnectionState { CONNECTING,READY,UNAVAILABLE,NEEDS_CONFIGURATION }
data class ConnectionStatus(val key:String,val label:String,val state:ConnectionState,val detail:String)
data class LiveSegmentConfig(val segmentId:String,val approachId:String,val latitude:Double,val longitude:Double,val approachHeadingDegrees:Double,val startRouteIndex:Int,val endRouteIndex:Int,val turn:TurnSignal,val radiusMeters:Double=160.0,val headingToleranceDegrees:Double=30.0,val maxTbtDistanceMeters:Double=200.0)
data class LiveRouteConfig(val routeId:String,val nativeRouteId:String,val segments:List<LiveSegmentConfig>) {
 init {
  require(routeId==SampleRoute.ID && nativeRouteId.isNotBlank()){"경로 ID를 확인하세요."}
  require(segments.size==2 && segments.map{it.segmentId}.toSet()==SampleRoute.segments.map{it.id}.toSet()){"두 연습 구간이 필요해요."}
  segments.forEach { s->
   val known=SampleRoute.segment(s.segmentId)!!
   require(s.approachId==known.approachId && s.turn==known.direction)
   require(s.latitude.isFinite() && s.latitude in -90.0..90.0 && s.longitude.isFinite() && s.longitude in -180.0..180.0)
   require(s.approachHeadingDegrees.isFinite() && s.approachHeadingDegrees in 0.0..360.0)
   require(s.startRouteIndex>=0 && s.endRouteIndex>=s.startRouteIndex)
   require(s.radiusMeters in 10.0..300.0 && s.headingToleranceDegrees in 1.0..60.0 && s.maxTbtDistanceMeters in 10.0..400.0)
  }
 }
}
/** Real values only. All callbacks and SDK lifecycle changes run on the main thread. */
class LiveTelemetry(context:Context,private val onSnapshot:(Snapshot)->Unit,private val onStatus:(List<ConnectionStatus>)->Unit,private val onEditingAllowed:(Boolean)->Unit) {
 private val context=context.applicationContext
 private val handler=Handler(Looper.getMainLooper())
 private var running=false
 private var generation=0
 private var vehicle:Vehicle?=null
 private var navi:NaviHelper?=null
 private var car:CarMonitor?=null
 private var config:LiveRouteConfig?=null
 private val statuses=linkedMapOf<String,ConnectionStatus>()
 private var speed:Double?=null;private var speedAt=0L
 private var brake:Boolean?=null;private var brakeAt=0L
 private var signal=TurnSignal.UNKNOWN;private var signalAt=0L
 private var location:CurrentLocationInfo?=null;private var locationAt=0L
 private var routing=false;private var routeAt=0L
 private var instructions:List<TBTInfo> = emptyList();private var tbtAt=0L
 private var brakeListener:BrakePedalStatusListener?=null
 private var signalListener:TurnSignalStateListener?=null
 private var navListener:NaviHelperEventListener?=null
 private var lastMatched:LiveSegmentConfig?=null
 private var lastPoll=0L
 private fun dispatch(action:()->Unit){if(Looper.myLooper()==Looper.getMainLooper())action()else handler.post(action)}
 private fun active(token:Int,action:()->Unit)=dispatch{if(running&&generation==token)action()}
 private fun status(key:String,label:String,state:ConnectionState,detail:String){
  val item=ConnectionStatus(key,label,state,detail)
  if(statuses[key]!=item){statuses[key]=item;onStatus(statuses.values.toList())}
 }
 private fun fresh(now:Long,at:Long)=at>0&&at<=now&&now-at<=2500
 private fun error(e:Throwable)=e.javaClass.simpleName+": "+e.message.orEmpty().take(120)
 fun configureRoute(value:LiveRouteConfig?)=dispatch{config=value;lastMatched=null;reportConfig()}
 private fun reportConfig(){status("calibration","연습 경로",if(config==null)ConnectionState.NEEDS_CONFIGURATION else ConnectionState.READY,if(config==null)"실제 경로 JSON을 등록하세요."else "실제 내비 경로 ID·위치·방향을 함께 확인해요.")}
 fun start()=dispatch{
  if(running)return@dispatch
  running=true;generation++;val token=generation;statuses.clear();reportConfig();onEditingAllowed(false)
  if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)){
   status("platform","차량 플랫폼",ConnectionState.UNAVAILABLE,"Android Automotive 기기가 아니에요. 시나리오 재생을 사용할 수 있어요.");return@dispatch
  }
  status("platform","차량 플랫폼",ConnectionState.READY,"Android Automotive 확인")
  connectCar(token);connectVehicle(token);connectNavi(token);handler.post(tick)
 }
 private fun connectCar(token:Int){
  try {
   car=CarMonitor(context,{v,at->active(token){speed=v;speedAt=at;status("speed","차량 속도",if(v==null)ConnectionState.UNAVAILABLE else ConnectionState.READY,if(v==null)"속도 확인 불가"else "실제 속도 수신 · m/s → km/h")}},
    {allowed->active(token){onEditingAllowed(allowed);status("ux","주차·조작 허용",ConnectionState.READY,if(allowed)"P 기어와 조작 허용 확인"else "주차 또는 플랫폼 조작 허용 대기")}},
    {key,message->active(token){if(key=="speed"){speed=null;speedAt=0}else onEditingAllowed(false);status(key,if(key=="speed")"차량 속도"else "주차·조작 허용",ConnectionState.UNAVAILABLE,message)}}).also{it.start()}
  }catch(e:Exception){status("car","차량 연결",ConnectionState.UNAVAILABLE,error(e));onEditingAllowed(false)}
  catch(e:LinkageError){status("car","차량 연결",ConnectionState.UNAVAILABLE,error(e));onEditingAllowed(false)}
 }
 private fun acceptBrake(v:Boolean?){brake=v;brakeAt=System.currentTimeMillis();status("brake","브레이크 페달",if(v==null)ConnectionState.UNAVAILABLE else ConnectionState.READY,when(v){true->"페달 눌림 수신";false->"페달 해제 수신";null->"확인 불가"})}
 private fun acceptSignal(v:TurnSignalValue?){signal=when(v){TurnSignalValue.LEFT->TurnSignal.LEFT;TurnSignalValue.RIGHT->TurnSignal.RIGHT;TurnSignalValue.OFF->TurnSignal.OFF;else->TurnSignal.UNKNOWN};signalAt=System.currentTimeMillis();status("signal","방향지시등",if(signal==TurnSignal.UNKNOWN)ConnectionState.UNAVAILABLE else ConnectionState.READY,signal.label+" 상태 수신")}
 private fun brakeError(e:Throwable){brake=null;brakeAt=0;status("brake","브레이크 페달",ConnectionState.UNAVAILABLE,error(e))}
 private fun signalError(e:Throwable){signal=TurnSignal.UNKNOWN;signalAt=0;status("signal","방향지시등",ConnectionState.UNAVAILABLE,error(e))}
 private fun connectVehicle(token:Int){
  status("brake","브레이크 페달",ConnectionState.CONNECTING,"지원 확인 및 값 수신 대기")
  status("signal","방향지시등",ConnectionState.CONNECTING,"지원 확인 및 값 수신 대기")
  try {
   val client=Vehicle(context);vehicle=client;client.initialize()
   val bl=object:BrakePedalStatusListener {override fun onBrakePedalStatusUpdated(status:Boolean?){active(token){acceptBrake(status)}};override fun onFailed(e:Exception){active(token){brakeError(e)}}}
   val sl=object:TurnSignalStateListener {override fun onTurnSignalStateUpdated(state:TurnSignalValue?){active(token){acceptSignal(state)}};override fun onFailed(e:Exception){active(token){signalError(e)}}}
   brakeListener=bl;signalListener=sl
   client.getBrake().checkBrakePedalStatusCapabilities(onComplete={active(token){runCatching{client.getBrake().registerBrakePedalStatus(bl);readVehicle(token)}.onFailure{brakeError(it)}}},onFailed={active(token){brakeError(it)}})
   client.getTurnSignal().checkTurnSignalStateCapabilities(onComplete={active(token){runCatching{client.getTurnSignal().registerTurnSignalState(sl);readVehicle(token)}.onFailure{signalError(it)}}},onFailed={active(token){signalError(it)}})
  }catch(e:Exception){brakeError(e);signalError(e)}catch(e:LinkageError){brakeError(e);signalError(e)}
 }
 private fun readVehicle(token:Int){
  runCatching{vehicle?.getBrake()?.getCurrentBrakePedalStatus(onComplete={active(token){acceptBrake(it)}},onFailed={active(token){brakeError(it)}})}.onFailure{brakeError(it)}
  runCatching{vehicle?.getTurnSignal()?.getCurrentTurnSignalState(onComplete={active(token){acceptSignal(it)}},onFailed={active(token){signalError(it)}})}.onFailure{signalError(it)}
 }
 private fun invalidate(reason:String,clearConfig:Boolean=false){
  routing=false;routeAt=0;location=null;locationAt=0;instructions=emptyList();tbtAt=0;lastMatched=null
  if(clearConfig){config=null;reportConfig()}
  status("navigation","기본 내비",ConnectionState.UNAVAILABLE,reason);emit()
 }
 private fun connectNavi(token:Int){
  status("navigation","기본 내비",ConnectionState.CONNECTING,"위치·경로·회전 정보 대기")
  try{
   val listener=object:NaviHelperEventListener{
    override fun onCurrentLocationInfo(currentLocationInfo:CurrentLocationInfo){active(token){
     val old=location?.routeId
     if(old!=null && old!=currentLocationInfo.routeId){instructions=emptyList();tbtAt=0;lastMatched=null}
     location=currentLocationInfo;locationAt=System.currentTimeMillis()
     status("navigation","기본 내비",ConnectionState.READY,"위치 응답 수신 · routeId="+currentLocationInfo.routeId+" · index="+currentLocationInfo.currentRouteIndex)
    }}
    override fun onRouteStateInfo(routeStateInfo:RouteStateInfo){active(token){routing=routeStateInfo.isRouting;routeAt=System.currentTimeMillis();if(!routing){lastMatched=null;emit()}}}
    override fun onTBTInfo(tbtInfoList:List<TBTInfo>){active(token){instructions=tbtInfoList;tbtAt=System.currentTimeMillis()}}
    override fun onRouteStarted(routeStartInfo:RouteStartInfo){active(token){invalidate("새 경로 확인 중");pollNavigation()}}
    override fun onRouteCancelled(){active(token){invalidate("경로 취소 · 추가 안내 종료")}}
    override fun onDestinationArrived(destinationArrivedInfo:DestinationArrivedInfo){active(token){invalidate("목적지 도착")}}
    override fun onReRouteEnded(){active(token){invalidate("경로 재탐색 · 등록을 다시 확인하세요.",true);pollNavigation()}}
    override fun onDestinationChanged(){active(token){invalidate("목적지 변경 · 경로 등록 필요",true);pollNavigation()}}
    override fun onRouteOptionChanged(routeOption:RouteOption){active(token){invalidate("경로 옵션 변경",true);pollNavigation()}}
    override fun onWaypointChanged(waypointChangedInfo:WaypointChangedInfo){active(token){invalidate("경유지 변경",true);pollNavigation()}}
    override fun onError(errorCode:NaviErrorCode){active(token){invalidate(errorCode.toString())}}
   }
   navListener=listener;navi=NaviHelper(context).also{it.initialize();it.addListener(listener)};pollNavigation()
  }catch(e:Exception){invalidate(error(e))}catch(e:LinkageError){invalidate(error(e))}
 }
 private fun pollNavigation(){runCatching{navi?.getRouteStateInfo();navi?.getCurrentLocationInfo();navi?.getTBTInfo()}.onFailure{invalidate(error(it))}}
 private val tick=object:Runnable{override fun run(){
  if(!running)return
  val now=System.currentTimeMillis()
  if(now-lastPoll>=1000){lastPoll=now;readVehicle(generation);pollNavigation()}
  emit();handler.postDelayed(this,500)
 }}
 private fun emit(){
  val now=System.currentTimeMillis();val c=config;val loc=location
  val routeMatches=c!=null && routing && (loc==null || loc.routeId==c.nativeRouteId)
  val reliable=routeMatches && fresh(now,routeAt)&&fresh(now,locationAt)&&loc!=null&&loc.currentRouteIndex!=null&&loc.latitude.isFinite()&&loc.longitude.isFinite()&&loc.heading.isFinite()
  val match=if(reliable&&fresh(now,tbtAt)&&c!=null&&loc!=null)match(c,loc)else null
  if(reliable)lastMatched=match
  val effective=if(reliable)match else if(routeMatches)lastMatched else null
  onSnapshot(Snapshot(timestampMs=now,source=Source.LIVE,routeId=SampleRoute.ID,routeActive=routeMatches,segmentId=effective?.segmentId,approachId=effective?.approachId,approachVerified=effective!=null,locationReliable=reliable,locationTimestampMs=locationAt,speedKmh=if(fresh(now,speedAt))speed else null,speedTimestampMs=speedAt,brake=if(fresh(now,brakeAt))brake else null,brakeTimestampMs=brakeAt,signal=if(fresh(now,signalAt))signal else TurnSignal.UNKNOWN,signalTimestampMs=signalAt))
 }
 private fun match(c:LiveRouteConfig,loc:CurrentLocationInfo):LiveSegmentConfig? {
  val index=loc.currentRouteIndex?:return null;val next=instructions.firstOrNull()?:return null
  val turn=when(next.type){TurnType.TURN_LEFT->TurnSignal.LEFT;TurnType.TURN_RIGHT->TurnSignal.RIGHT;else->return null}
  if(!next.distance.isFinite()||next.distance<0)return null
  return c.segments.filter{s->index in s.startRouteIndex..s.endRouteIndex && turn==s.turn && next.distance<=s.maxTbtDistanceMeters &&
   abs(((loc.heading-s.approachHeadingDegrees)%360+540)%360-180)<=s.headingToleranceDegrees &&
   distance(loc.latitude,loc.longitude,s.latitude,s.longitude)<=s.radiusMeters}.singleOrNull()
 }
 private fun distance(a:Double,b:Double,c:Double,d:Double):Double{
  val p1=Math.toRadians(a);val p2=Math.toRadians(c);val dp=Math.toRadians(c-a);val dl=Math.toRadians(d-b)
  return 6371000*2*asin(sqrt((sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2)).coerceIn(0.0,1.0)))
 }
 fun stop()=dispatch{
  if(!running)return@dispatch
  running=false;generation++;handler.removeCallbacks(tick);onEditingAllowed(false)
  brakeListener?.let{runCatching{vehicle?.getBrake()?.unregisterBrakePedalStatus(it)}}
  signalListener?.let{runCatching{vehicle?.getTurnSignal()?.unregisterTurnSignalState(it)}}
  runCatching{vehicle?.release()};vehicle=null;brakeListener=null;signalListener=null
  navListener?.let{runCatching{navi?.removeListener(it)}};runCatching{navi?.release()};navi=null;navListener=null
  runCatching{car?.stop()};car=null
  speed=null;speedAt=0;brake=null;brakeAt=0;signal=TurnSignal.UNKNOWN;signalAt=0;location=null;locationAt=0;routing=false;routeAt=0;lastMatched=null;instructions=emptyList();tbtAt=0
 }
}
