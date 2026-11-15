package ai.pleos.coach.ui

import ai.pleos.coach.R
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

internal data class CoachColors(
 val background:Color, val surface:Color, val text:Color, val secondary:Color,
 val accent:Color, val selected:Color, val line:Color, val disabled:Color
)
internal val DayColors=CoachColors(Color(0xFFF3F5F7),Color.White,Color(0xFF17202D),Color(0xFF536173),Color(0xFF0054AD),Color(0xFFE5EFFB),Color(0xFFD7DDE5),Color(0xFF7B8491))
internal val NightColors=CoachColors(Color(0xFF10151C),Color(0xFF1A222D),Color(0xFFF2F5FA),Color(0xFFB7C3D2),Color(0xFF91C0FF),Color(0xFF243B57),Color(0xFF3B495A),Color(0xFF8793A3))
internal val LocalCoachColors=staticCompositionLocalOf{DayColors}
@OptIn(ExperimentalTextApi::class)
internal val Asta=FontFamily(
 Font(R.font.asta_sans,weight=FontWeight.Light,variationSettings=FontVariation.Settings(FontVariation.weight(300))),
 Font(R.font.asta_sans,weight=FontWeight.Normal,variationSettings=FontVariation.Settings(FontVariation.weight(400))),
 Font(R.font.asta_sans,weight=FontWeight.Medium,variationSettings=FontVariation.Settings(FontVariation.weight(500))),
 Font(R.font.asta_sans,weight=FontWeight.SemiBold,variationSettings=FontVariation.Settings(FontVariation.weight(600)))
)
private val Corner=RoundedCornerShape(8.dp)

@Composable fun CoachApp(state:CoachUiState,actions:CoachActions) {
 val dark=isSystemInDarkTheme() || state.screen==CoachScreen.DRIVING
 val colors=if(dark)NightColors else DayColors
 val type=TextStyle(fontFamily=Asta,fontSize=24.sp,lineHeight=34.sp,color=colors.text)
 val typography=Typography(
  bodyLarge=type,bodyMedium=type,bodySmall=type.copy(fontSize=20.sp,lineHeight=28.sp),
  titleLarge=type.copy(fontSize=32.sp,lineHeight=42.sp,fontWeight=FontWeight.SemiBold),
  titleMedium=type.copy(fontSize=28.sp,lineHeight=38.sp,fontWeight=FontWeight.SemiBold),
  labelLarge=type.copy(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.Medium),
  labelMedium=type.copy(fontSize=22.sp,lineHeight=30.sp),
  headlineLarge=type.copy(fontSize=40.sp,lineHeight=52.sp,fontWeight=FontWeight.SemiBold)
 )
 val scheme=if(dark)darkColorScheme(primary=colors.accent,onPrimary=Color(0xFF06264C),surface=colors.surface,background=colors.background,onSurface=colors.text,onSurfaceVariant=colors.secondary,outline=colors.line)
 else lightColorScheme(primary=colors.accent,onPrimary=Color.White,surface=colors.surface,background=colors.background,onSurface=colors.text,onSurfaceVariant=colors.secondary,outline=colors.line)
 CompositionLocalProvider(LocalCoachColors provides colors) {
  MaterialTheme(colorScheme=scheme,typography=typography) { CoachContent(state,actions) }
 }
}

@Composable private fun CoachContent(s:CoachUiState,a:CoachActions) {
 val c=LocalCoachColors.current
 var connections by remember{mutableStateOf(false)}
 var dirty by remember{mutableStateOf(false)}
 var pending by remember{mutableStateOf<(() -> Unit)?>(null)}
 fun guarded(action:()->Unit){if(dirty)pending=action else action()}
 Row(Modifier.fillMaxSize().background(c.background).imePadding()) {
  Column(Modifier.width(204.dp).fillMaxHeight().background(c.surface).padding(horizontal=20.dp,vertical=32.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    PixelMark(Modifier.size(30.dp));Spacer(Modifier.width(14.dp))
    Text("동행",fontSize=36.sp,fontWeight=FontWeight.SemiBold)
   }
   Text("운전 연습",fontSize=22.sp,color=c.secondary,modifier=Modifier.padding(top=8.dp,start=44.dp))
   Spacer(Modifier.height(48.dp))
   val pages=if(s.recording)listOf(CoachScreen.DRIVING) else listOf(CoachScreen.PREPARE,CoachScreen.REVIEW,CoachScreen.SETTINGS)
   pages.forEach {page->
    val selected=s.screen==page
    Surface(color=if(selected)c.selected else Color.Transparent,shape=Corner,
     modifier=Modifier.fillMaxWidth().heightIn(min=88.dp).selectable(selected=selected,enabled=s.editingAllowed&&!s.recording,role=Role.Tab){guarded{a.onScreen(page)}}) {
     Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.width(4.dp).height(28.dp).background(if(selected)c.accent else Color.Transparent))
      Spacer(Modifier.width(14.dp))
      Text(page.label,fontSize=25.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,color=if(selected)c.accent else c.text)
     }
    }
    Spacer(Modifier.height(16.dp))
   }
   Spacer(Modifier.weight(1f))
   Text(if(s.editingAllowed)"P  주차 상태" else "조작 제한",fontSize=22.sp,color=c.secondary)
   Spacer(Modifier.height(24.dp))
   ActionButton("연결 정보",{connections=true},Modifier.fillMaxWidth(),enabled=s.editingAllowed&&!s.recording)
  }
  Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
  Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=36.dp,vertical=24.dp)) {
   Row(Modifier.fillMaxWidth().heightIn(min=76.dp),verticalAlignment=Alignment.CenterVertically) {
    Text(s.screen.label,fontSize=36.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
    Text(if(s.mode==InputMode.REPLAY)"시뮬레이션" else "차량 데이터",color=c.accent,fontSize=24.sp,fontWeight=FontWeight.Medium)
   }
   HorizontalDivider(color=c.line)
   Spacer(Modifier.height(24.dp))
   // Notifications stay out of the limited driving view.
   if(s.screen!=CoachScreen.DRIVING) s.statusMessage?.let{
    Text(it,fontSize=22.sp,lineHeight=30.sp,color=c.accent,modifier=Modifier.padding(bottom=16.dp),maxLines=2,overflow=TextOverflow.Ellipsis)
   }
   if(!s.editingAllowed && s.screen!=CoachScreen.DRIVING) ParkedOnly()
   else Box(Modifier.weight(1f)){
    when(s.screen){
     CoachScreen.PREPARE->PrepareScreen(s,a)
     CoachScreen.DRIVING->DrivingScreen(s,a)
     CoachScreen.REVIEW->ReviewScreen(s,a)
     CoachScreen.SETTINGS->SettingsScreen(s,a,{dirty=it},{id->guarded{dirty=false;a.onSelectSegment(id)}})
    }
   }
  }
 }
 if(connections&&s.editingAllowed&&!s.recording) ConnectionSheet(s,a){connections=false}
 if(pending!=null&&s.editingAllowed) ChoiceDialog("변경 내용을 저장하지 않았습니다",{
  Text("이 화면을 나가면 수정한 선택이 취소됩니다.")
 },{
  ActionButton("계속 수정",{pending=null},Modifier.weight(1f),primary=true)
  ActionButton("변경 취소",{val action=pending;pending=null;dirty=false;action?.invoke()},Modifier.weight(1f))
 }){pending=null}
}

@Composable private fun PixelMark(modifier:Modifier) {
 val c=LocalCoachColors.current
 Canvas(modifier){val p=size.width/3.6f;listOf(0 to 0,1 to 0,0 to 1,1 to 1).forEach{(x,y)->drawRect(c.accent,androidx.compose.ui.geometry.Offset(x*p*1.6f,y*p*1.6f),androidx.compose.ui.geometry.Size(p,p))}}
}
@Composable internal fun ActionButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,primary:Boolean=false,enabled:Boolean=true) {
 val c=LocalCoachColors.current
 if(primary) Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=80.dp),shape=Corner,contentPadding=PaddingValues(horizontal=24.dp,vertical=18.dp)){
  Text(label,fontSize=25.sp,fontWeight=FontWeight.SemiBold,maxLines=2,color=if(enabled)MaterialTheme.colorScheme.onPrimary else c.disabled)
 } else OutlinedButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=80.dp),shape=Corner,border=BorderStroke(1.dp,if(enabled)c.line else c.line.copy(alpha=.5f)),contentPadding=PaddingValues(horizontal=20.dp,vertical=18.dp)){
  Text(label,fontSize=24.sp,maxLines=2,color=if(enabled)c.text else c.disabled)
 }
}
@Composable private fun ChoiceDialog(title:String,body:@Composable ColumnScope.()->Unit,buttons:@Composable RowScope.()->Unit,onDismiss:()->Unit) {
 val c=LocalCoachColors.current
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(color=c.surface,shape=RoundedCornerShape(12.dp),modifier=Modifier.widthIn(max=1080.dp).fillMaxWidth(.86f).fillMaxHeight(.88f)){
   Column(Modifier.padding(32.dp)){
    Text(title,fontSize=32.sp,fontWeight=FontWeight.SemiBold)
    Spacer(Modifier.height(24.dp))
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp),content=body)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp),content=buttons)
   }
  }
 }
}
@Composable private fun ConnectionSheet(s:CoachUiState,a:CoachActions,onClose:()->Unit) {
 val c=LocalCoachColors.current
 ChoiceDialog("연결 정보",{
  Text(if(s.mode==InputMode.REPLAY)"시뮬레이션 기록은 차량 데이터와 별도로 저장됩니다." else "차량에서 수신한 값만 기록합니다.",color=c.secondary)
  s.connections.forEach{x->
   HorizontalDivider(color=c.line)
   Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(24.dp)){
    Text(when(x.available){true->"수신";false->"확인 필요";null->"대기"},color=c.accent,modifier=Modifier.width(120.dp),fontSize=22.sp)
    Column{Text(x.label,fontSize=26.sp,fontWeight=FontWeight.Medium);Text(x.detail,color=c.secondary,fontSize=22.sp,lineHeight=30.sp)}
   }
  }
  ActionButton("실제 경로 파일 불러오기",a.onImportRoute,Modifier.fillMaxWidth())
 },{
  ActionButton("닫기",onClose,Modifier.weight(1f),primary=true)
  ActionButton("다시 연결",a.onConnect,Modifier.weight(1f))
 },onClose)
}
@Composable private fun ParkedOnly(){
 val c=LocalCoachColors.current
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center){
  Text("P",fontSize=88.sp,color=c.accent,fontWeight=FontWeight.Medium)
  Text("주차 후 이용할 수 있습니다",fontSize=36.sp)
  Spacer(Modifier.height(20.dp));Text("복습과 설정은 P 기어 및 차량의 조작 허용 상태에서 열립니다.",color=c.secondary)
 }
}
@Composable private fun ModeSelector(s:CoachUiState,a:CoachActions,modifier:Modifier=Modifier){
 val c=LocalCoachColors.current
 Row(modifier.border(1.dp,c.line,Corner).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
  InputMode.entries.forEach{m->
   val selected=m==s.mode
   Surface(color=if(selected)c.selected else Color.Transparent,shape=RoundedCornerShape(6.dp),modifier=Modifier.weight(1f).heightIn(min=76.dp).selectable(selected,role=Role.Tab){a.onMode(m)}){
    Box(contentAlignment=Alignment.Center){Text(if(m==InputMode.REPLAY)"시뮬레이션"else"차량 연결",fontSize=24.sp,color=if(selected)c.accent else c.secondary,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)}
   }
  }
 }
}
@Composable private fun PrepareScreen(s:CoachUiState,a:CoachActions){
 val c=LocalCoachColors.current
 var scenarioPicker by remember{mutableStateOf(false)}
 val scenario=s.scenarios.find{it.id==s.selectedScenarioId}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)){
   ModeSelector(s,a,Modifier.weight(1f))
   if(s.mode==InputMode.REPLAY)ActionButton((scenario?.title?:"시나리오 선택")+"  변경",{scenarioPicker=true},Modifier.weight(1f))
   else Box(Modifier.weight(1f).heightIn(min=84.dp),contentAlignment=Alignment.CenterStart){Text(if(s.canStart)"경로 연결 준비 완료"else"연결 정보에서 실제 경로를 등록하세요",color=c.secondary)}
  }
  Spacer(Modifier.height(24.dp))
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
   Surface(color=c.surface,shape=Corner,modifier=Modifier.fillMaxWidth()){
    Column(Modifier.padding(32.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Text(if(s.mode==InputMode.REPLAY)"등교 연습 경로"else"등록한 연습 경로",fontSize=32.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
      Text("2개 구간",fontSize=24.sp,color=c.secondary)
     }
     Text(if(s.mode==InputMode.REPLAY)"가상 주행 · 약 31초" else "기본 내비의 경로 안내 사용",color=c.secondary,modifier=Modifier.padding(top=8.dp))
     RouteSketch(s.segments,Modifier.fillMaxWidth().height(248.dp))
     Text("연습 구간 도식 · 실제 지도가 아닙니다",fontSize=20.sp,color=c.secondary)
     Spacer(Modifier.height(24.dp))
     s.segments.forEachIndexed{i,x->
      HorizontalDivider(color=c.line)
      Row(Modifier.fillMaxWidth().heightIn(min=112.dp).clickable(role=Role.Button){a.onSelectSegment(x.id);a.onScreen(CoachScreen.SETTINGS)}.padding(vertical=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)){
       Text(if(i==0)"01"else"02",color=c.accent,fontSize=26.sp,fontWeight=FontWeight.Medium)
       TurnSymbol(x.direction,c.accent,Modifier.size(44.dp))
       Column(Modifier.weight(1f)){
        Text(x.title,fontSize=28.sp,fontWeight=FontWeight.Medium)
        Text(if(s.selectedScenarioId=="FIRST"&&s.mode==InputMode.REPLAY)"첫 주행 · 기록만"else if(x.settings.selected)x.settings.goal.label else "기록만",color=c.secondary,fontSize=24.sp)
       }
       Text(if(s.selectedScenarioId=="FIRST"&&s.mode==InputMode.REPLAY)"안내 없음" else if(x.settings.selected)x.settings.help.label else "미선택",fontSize=22.sp,color=c.secondary)
       Text("›",fontSize=40.sp,color=c.secondary)
      }
     }
    }
   }
  }
  Footer{
   ActionButton(if(s.mode==InputMode.REPLAY)"시뮬레이션 시작"else"주행 기록 시작",a.onStart,Modifier.widthIn(min=300.dp),primary=true,enabled=s.canStart)
   Column(Modifier.weight(1f)){
    Text(if(s.mode==InputMode.REPLAY)"차량을 움직이지 않는 연습입니다" else "차량 데이터로 연습을 기록합니다",fontSize=24.sp)
    Text(if(s.mode==InputMode.REPLAY)"기록을 마치면 구간별 복습으로 이동합니다."else"주차한 상태에서 시작하세요.",fontSize=22.sp,color=c.secondary)
   }
  }
 }
 if(scenarioPicker&&s.editingAllowed)ChoiceDialog("시뮬레이션 선택",{
  s.scenarios.forEach{x->SelectRow(x.title,s.selectedScenarioId==x.id,x.description){a.onScenario(x.id);scenarioPicker=false}}
 },{ActionButton("닫기",{scenarioPicker=false},Modifier.fillMaxWidth())}){scenarioPicker=false}
}
@Composable private fun Footer(content:@Composable RowScope.()->Unit){
 val c=LocalCoachColors.current
 Column{
  Spacer(Modifier.height(24.dp));HorizontalDivider(color=c.line);Spacer(Modifier.height(24.dp))
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp),content=content)
 }
}
internal fun signalLabel(v:String?):String=when(v){"LEFT"->"좌측";"RIGHT"->"우측";"OFF"->"꺼짐";else->"확인 불가"}
@Composable private fun DrivingScreen(s:CoachUiState,a:CoachActions){
 val c=LocalCoachColors.current
 val active=s.segments.find{it.id==s.activeSegmentId}
 Column(Modifier.fillMaxSize()){
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)){
   Text(if(s.replayPaused)"일시 정지"else if(active!=null)"구간 기록 중"else"구간 진입 대기",color=c.accent,fontSize=26.sp)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(32.dp)){
    if(active!=null)TurnSymbol(active.direction,c.accent,Modifier.size(96.dp))
    Text(active?.title?:"연습 경로",fontSize=48.sp,fontWeight=FontWeight.Medium)
   }
   Text(if(active==null)"구간 진입을 기다립니다" else s.drivingMessage,fontSize=32.sp,lineHeight=44.sp,maxLines=3)
   Text(if(s.mode==InputMode.REPLAY)"시뮬레이션 데이터"else"기본 내비의 길 안내를 확인하세요",fontSize=24.sp,color=c.secondary)
   Spacer(Modifier.height(20.dp))
   HorizontalDivider(color=c.line)
   DrivingTelemetry(s,Modifier.fillMaxWidth())
   if(s.mode==InputMode.REPLAY){
    LinearProgressIndicator(progress={s.progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp),color=c.accent,trackColor=c.line)
    Text("재생 "+(s.progress*100).toInt()+"%",fontSize=22.sp,color=c.secondary)
   }
  }
  Footer {
   if(s.mode==InputMode.REPLAY){
    ActionButton(if(s.replayPaused)"계속 재생"else"일시 정지",a.onPause,Modifier.width(240.dp))
    ActionButton("종료 후 복습",a.onStop,Modifier.width(260.dp),primary=true)
   }else ActionButton("기록 마치기",a.onStop,Modifier.width(280.dp),enabled=s.editingAllowed)
   Text(if(s.mode==InputMode.LIVE&&!s.editingAllowed)"주차 후 기록을 마칠 수 있습니다"else s.audioStatus.ifBlank{"구간당 안내 최대 1회"},fontSize=22.sp,color=c.secondary,modifier=Modifier.weight(1f))
  }
 }
}
@Composable private fun SegmentTabs(s:CoachUiState,onSelect:(String)->Unit){
 val c=LocalCoachColors.current
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)){
  s.segments.forEachIndexed{i,x->
   val selected=s.selectedSegmentId==x.id
   Surface(color=if(selected)c.selected else c.surface,shape=Corner,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)c.accent else c.line),
    modifier=Modifier.weight(1f).heightIn(min=80.dp).selectable(selected,role=Role.Tab){onSelect(x.id)}){
    Row(Modifier.padding(horizontal=24.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
     Text(if(i==0)"01"else"02",color=c.accent);Text(x.title,fontSize=26.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
    }
   }
  }
 }
}
@Composable private fun ReviewScreen(s:CoachUiState,a:CoachActions){
 val c=LocalCoachColors.current
 val segment=s.segments.find{it.id==s.selectedSegmentId}?:s.segments.firstOrNull()
 val records=s.records.filter{it.segmentId==segment?.id&&it.approachKey==segment?.approachKey}.take(2)
 Column(Modifier.fillMaxSize()){
  SegmentTabs(s,a.onSelectSegment)
  Spacer(Modifier.height(24.dp))
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
   if(records.isEmpty()){
    Text("저장된 기록이 없습니다",fontSize=32.sp,modifier=Modifier.padding(top=40.dp))
    Text("출발 준비에서 주행을 기록하면 이곳에서 비교할 수 있습니다.",color=c.secondary,modifier=Modifier.padding(top=16.dp))
   }else{
    Text("최근 두 번의 기록",fontSize=30.sp,fontWeight=FontWeight.Medium)
    Text("같은 구간 · 같은 접근 방향 · 진입 후 시간 기준",color=c.secondary,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
    val maxTime=records.maxOf{it.durationSeconds}.coerceAtLeast(1f)
    val maxSpeed=maxOf(60f,records.flatMap{it.samples}.mapNotNull{it.speedKmh}.maxOrNull()?.let{kotlin.math.ceil(it/20f)*20f}?:60f)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)){
     records.forEachIndexed{i,r->
      Surface(color=c.surface,shape=Corner,modifier=Modifier.weight(1f)){
       Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(if(i==0)"최근 기록"else"이전 기록",fontSize=28.sp,fontWeight=FontWeight.Medium)
        Text(r.timeLabel,fontSize=22.sp,color=c.secondary)
        SpeedPlot(r,maxTime,maxSpeed,Modifier.fillMaxWidth().height(330.dp))
        Text("진입 후 "+String.format(Locale.KOREA,"%.1f",r.durationSeconds)+"초 · 속도 누락 "+r.samples.count{it.speedKmh==null}+"개",fontSize=20.sp,color=c.secondary)
        HorizontalDivider(color=c.line)
        Text(r.decisionLabel,fontSize=25.sp,fontWeight=FontWeight.Medium,color=c.accent)
        Text(r.decisionDetail,fontSize=22.sp,lineHeight=32.sp)
        r.difficultyLabel?.let{Text("나의 평가  "+it,fontSize=22.sp,color=c.secondary)}
        if(r.note.isNotBlank())Text(r.note,fontSize=22.sp,color=c.secondary)
       }
      }
     }
     if(records.size==1)Box(Modifier.weight(1f).padding(24.dp)){Text("다음 기록부터\n두 번의 주행을 비교합니다",fontSize=28.sp,lineHeight=40.sp,color=c.secondary)}
    }
    Text("주변 교통 상황은 기록되지 않습니다. 그래프는 운전 점수나 안전성 평가가 아닙니다.",color=c.secondary,fontSize=22.sp,lineHeight=32.sp,modifier=Modifier.padding(top=24.dp))
   }
  }
  Footer{
   ActionButton("다음 연습 설정",{a.onScreen(CoachScreen.SETTINGS)},Modifier.widthIn(min=300.dp),primary=true)
   ActionButton("출발 준비",{a.onScreen(CoachScreen.PREPARE)},Modifier.widthIn(min=220.dp))
  }
 }
}
@Composable private fun SettingsScreen(s:CoachUiState,a:CoachActions,onDirty:(Boolean)->Unit,onSegment:(String)->Unit){
 val c=LocalCoachColors.current
 val segment=s.segments.find{it.id==s.selectedSegmentId}?:return
 var draft by remember(segment.id,segment.settings){mutableStateOf(segment.settings)}
 var page by rememberSaveable{mutableIntStateOf(0)}
 LaunchedEffect(draft,segment.settings){onDirty(draft!=segment.settings)}
 Column(Modifier.fillMaxSize()){
  SegmentTabs(s,onSegment)
  Spacer(Modifier.height(24.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
   listOf("연습 설정","지난 주행 평가").forEachIndexed{i,label->
    val selected=page==i
    Surface(color=if(selected)c.selected else Color.Transparent,shape=Corner,modifier=Modifier.weight(1f).heightIn(min=76.dp).selectable(selected,role=Role.Tab){page=i}){
     Box(contentAlignment=Alignment.Center){Text(label,fontSize=26.sp,color=if(selected)c.accent else c.secondary,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)}
    }
   }
  }
  Spacer(Modifier.height(24.dp))
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)){
   if(page==0){
    Surface(color=c.surface,shape=Corner,modifier=Modifier.fillMaxWidth().heightIn(min=96.dp).selectable(draft.selected,role=Role.Switch){draft=draft.copy(selected=!draft.selected)}){
     Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){
      Text("다음 주행에서 이 구간 연습",fontSize=28.sp,modifier=Modifier.weight(1f))
      Text(if(draft.selected)"사용"else"사용 안 함",color=c.secondary,modifier=Modifier.padding(end=24.dp))
      Switch(checked=draft.selected,onCheckedChange=null)
     }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(32.dp)){
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Text("연습 목표",fontSize=28.sp,fontWeight=FontWeight.Medium)
      LearningGoal.entries.forEach{g->SelectRow(g.label,draft.goal==g,leading={LearningGoalImage(g,Modifier.size(52.dp))}){draft=draft.copy(goal=g)}}
     }
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Text("도움 수준",fontSize=28.sp,fontWeight=FontWeight.Medium)
      HelpLevel.entries.forEach{h->SelectRow(h.label,draft.help==h){draft=draft.copy(help=h)}}
     }
    }
    Text(if(!draft.selected)"이 구간은 기록만 합니다. 연습을 켜면 선택한 목표가 적용됩니다." else if(draft.help==HelpLevel.REVIEW_ONLY)"음성 안내 없이 기록하고 주차 후 복습합니다." else when(draft.goal){LearningGoal.ROUTE_PREVIEW->"선택한 구간에 접근하면 한 번 안내합니다.";LearningGoal.SIGNAL_CHECK->"해당 방향의 신호가 켜져 있으면 안내를 생략합니다.";LearningGoal.SPEED_REVIEW->"속도와 페달을 기록합니다. 음성 안내 없이 주차 후 복습합니다."},fontSize=24.sp,lineHeight=34.sp,color=c.secondary)
   }else{
    Text("체감 난도",fontSize=28.sp,fontWeight=FontWeight.Medium)
    Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
     Difficulty.entries.forEach{d->Box(Modifier.weight(1f)){SelectRow(d.label,draft.difficulty==d){draft=draft.copy(difficulty=d)}}}
    }
    Text("어려웠던 이유",fontSize=28.sp,fontWeight=FontWeight.Medium)
    DifficultyReason.entries.forEach{r->SelectRow(r.label,draft.reason==r){draft=draft.copy(reason=r,goal=r.suggestion?:draft.goal)}}
    Text("이유를 선택하면 관련 연습 목표를 제안합니다. 연습 설정에서 변경할 수 있습니다.",fontSize=22.sp,lineHeight=32.sp,color=c.secondary)
    OutlinedTextField(value=draft.note,onValueChange={draft=draft.copy(note=it.take(120))},label={Text("메모 · 선택")},modifier=Modifier.fillMaxWidth().heightIn(min=96.dp),maxLines=3,shape=Corner,textStyle=MaterialTheme.typography.bodyLarge)
   }
  }
  Footer {
   ActionButton("설정 저장",{onDirty(false);a.onSaveSetting(draft);a.onScreen(CoachScreen.PREPARE)},Modifier.widthIn(min=300.dp),primary=true)
   Text(if(draft!=segment.settings)"저장하지 않은 변경 사항"else"다음 주행에 적용됩니다",fontSize=22.sp,color=c.secondary,modifier=Modifier.weight(1f))
  }
 }
}
@Composable private fun SelectRow(label:String,selected:Boolean,detail:String?=null,leading:(@Composable ()->Unit)?=null,onClick:()->Unit){
 val c=LocalCoachColors.current
 Surface(color=if(selected)c.selected else c.surface,shape=Corner,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)c.accent else c.line),
  modifier=Modifier.fillMaxWidth().heightIn(min=80.dp).selectable(selected,role=Role.RadioButton,onClick=onClick)){
  Row(Modifier.padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
   RadioButton(selected=selected,onClick=null,modifier=Modifier.size(32.dp))
   leading?.invoke()
   Column(Modifier.weight(1f)){
    Text(label,fontSize=24.sp,fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal)
    detail?.let{Text(it,fontSize=22.sp,lineHeight=30.sp,color=c.secondary,modifier=Modifier.padding(top=8.dp))}
   }
  }
 }
}
