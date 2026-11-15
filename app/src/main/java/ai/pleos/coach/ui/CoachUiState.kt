package ai.pleos.coach.ui
enum class CoachScreen(val label:String){ PREPARE("출발 준비"),DRIVING("주행 기록"),REVIEW("주행 복습"),SETTINGS("연습 설정") }
enum class InputMode(val label:String){ REPLAY("시나리오 재생"),LIVE("차량 연결") }
enum class HelpLevel(val label:String){ TOGETHER("기본 안내"),BRIEF("짧은 안내"),REVIEW_ONLY("안내 없이 기록") }
enum class LearningGoal(val label:String){ ROUTE_PREVIEW("회전 구간 예습"),SIGNAL_CHECK("방향지시등 확인"),SPEED_REVIEW("속도·브레이크 복습") }
enum class Difficulty(val label:String){ EASY("여유 있음"),MEDIUM("보통"),HARD("어려움") }
enum class DifficultyReason(val label:String,val suggestion:LearningGoal?){ LATE_TURN("회전 지점 인지가 늦음",LearningGoal.ROUTE_PREVIEW),FORGOT_SIGNAL("방향지시등 확인을 놓침",LearningGoal.SIGNAL_CHECK),SPEED_CONTROL("속도 조절이 어려움",LearningGoal.SPEED_REVIEW),TRAFFIC("앞차·신호 등 교통 상황",null),UNSURE("판단하기 어려움",null) }
data class SegmentSetting(val segmentId:String,val selected:Boolean=false,val reason:DifficultyReason=DifficultyReason.UNSURE,val goal:LearningGoal=LearningGoal.ROUTE_PREVIEW,val help:HelpLevel=HelpLevel.TOGETHER,val difficulty:Difficulty=Difficulty.MEDIUM,val note:String="")
data class UiSegment(val id:String,val title:String,val direction:String,val approachLabel:String,val settings:SegmentSetting,val approachKey:String=id)
data class UiSample(val elapsedSeconds:Float,val speedKmh:Float?,val brake:Boolean?,val signal:String?)
data class UiRecord(val id:String,val segmentId:String,val approachKey:String,val title:String,val timeLabel:String,val sourceLabel:String,val samples:List<UiSample>,val decisionLabel:String,val decisionDetail:String,val durationSeconds:Float,val difficultyLabel:String?=null,val note:String="")
data class UiScenario(val id:String,val title:String,val description:String)
data class UiConnection(val label:String,val detail:String,val available:Boolean?=null)
data class CoachUiState(val screen:CoachScreen=CoachScreen.PREPARE,val mode:InputMode=InputMode.REPLAY,val segments:List<UiSegment> = emptyList(),val selectedSegmentId:String="school-right",val records:List<UiRecord> = emptyList(),val scenarios:List<UiScenario> = emptyList(),val selectedScenarioId:String="FIRST",val editingAllowed:Boolean=true,val recording:Boolean=false,val replayPaused:Boolean=false,val canStart:Boolean=true,val speedKmh:Float?=null,val brake:Boolean?=null,val signal:String?=null,val activeSegmentId:String?=null,val progress:Float=0f,val drivingMessage:String="선택한 구간의 기록을 기다리고 있어요",val drivingDetail:String="기본 내비의 길 안내를 따라 이동하세요.",val connections:List<UiConnection> = emptyList(),val statusMessage:String?=null,val routeLabel:String="등교 연습 경로",val audioStatus:String="")
data class CoachActions(val onScreen:(CoachScreen)->Unit,val onMode:(InputMode)->Unit,val onScenario:(String)->Unit,val onStart:()->Unit,val onPause:()->Unit,val onStop:()->Unit,val onSelectSegment:(String)->Unit,val onSaveSetting:(SegmentSetting)->Unit,val onConnect:()->Unit,val onImportRoute:()->Unit={})
