package ai.pleos.coach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

enum class AudioResult { PLAYED, FOCUS_DENIED, UNAVAILABLE }
enum class AudioStage { STARTED, COMPLETED, FOCUS_DENIED, INTERRUPTED, UNAVAILABLE }
data class AudioPlaybackEvent(val prompt:String,val stage:AudioStage,val detail:String?=null)

/** Fixed speech files; no delayed focus, queues, or replay after interruption. */
class GuidanceAudio(context:Context,private val onEvent:(AudioPlaybackEvent)->Unit={}) {
 private val app=context.applicationContext
 private val manager=app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
 private val handler=Handler(Looper.getMainLooper())
 private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
 private var player:MediaPlayer?=null
 private var focusRequest:AudioFocusRequest?=null
 private var currentPrompt:String?=null
 private var released=false
 var lastEvent:AudioPlaybackEvent?=null
  private set
 private val listener=AudioManager.OnAudioFocusChangeListener { change ->
  if(change in listOf(AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
   finish(AudioStage.INTERRUPTED,"오디오 포커스를 상실하여 안내를 중단했어요.")
 }
 @Synchronized fun play(prompt:String):AudioResult {
  val audioManager=manager
  if(released || audioManager==null) { emit(prompt,AudioStage.UNAVAILABLE,"오디오 서비스를 사용할 수 없어요.");return AudioResult.UNAVAILABLE }
  val resourceName=when(prompt) {
   "지난번 선택한 연습 구간에 접근하고 있어요."->"route_together"
   "연습 구간이에요."->"route_brief"
   "연습 구간이에요. 방향지시등을 확인하세요."->"signal_together"
   "방향지시등을 확인하세요."->"signal_brief"
   else->null
  }
  if(resourceName==null || player!=null) {emit(prompt,AudioStage.UNAVAILABLE,"녹음이 없거나 앞선 안내가 재생 중이에요.");return AudioResult.UNAVAILABLE}
  val rawId=app.resources.getIdentifier(resourceName,"raw",app.packageName)
  if(rawId==0) {emit(prompt,AudioStage.UNAVAILABLE,"녹음 파일을 사용할 수 없어요.");return AudioResult.UNAVAILABLE}
  val candidate=MediaPlayer()
  try {
   candidate.setAudioAttributes(attributes)
   app.resources.openRawResourceFd(rawId).use { candidate.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
   candidate.prepare()
   if(candidate.duration<=750) {candidate.release();emit(prompt,AudioStage.UNAVAILABLE,"녹음 길이가 올바르지 않아요.");return AudioResult.UNAVAILABLE}
   currentPrompt=prompt;player=candidate
   candidate.setOnCompletionListener { completed -> synchronized(this) { if(player===completed) finish(AudioStage.COMPLETED) } }
   candidate.setOnErrorListener { failed,what,extra -> synchronized(this) { if(player===failed) finish(AudioStage.UNAVAILABLE,"음성 재생 오류: $what/$extra") };true }
   val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true)
    .setOnAudioFocusChangeListener(listener,handler).build()
   focusRequest=request
   if(audioManager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
    finish(AudioStage.FOCUS_DENIED,"오디오 포커스를 얻지 못해 안내를 생략했어요.");return AudioResult.FOCUS_DENIED
   }
   candidate.start();emit(prompt,AudioStage.STARTED);return AudioResult.PLAYED
  } catch(error:Exception) {
   if(player===candidate) finish(AudioStage.UNAVAILABLE,error.message) else {runCatching{candidate.release()};emit(prompt,AudioStage.UNAVAILABLE,error.message)}
   return AudioResult.UNAVAILABLE
  }
 }
 @Synchronized fun stop() {finish(AudioStage.INTERRUPTED,"현재 안내를 종료했어요.")}
 @Synchronized fun release() {stop();released=true}
 @Synchronized private fun finish(stage:AudioStage,detail:String?=null) {
  val prompt=currentPrompt;val previous=player;val focus=focusRequest
  player=null;focusRequest=null;currentPrompt=null
  runCatching {previous?.setOnCompletionListener(null);previous?.setOnErrorListener(null);previous?.release()}
  if(focus!=null) runCatching{manager?.abandonAudioFocusRequest(focus)}
  if(prompt!=null) emit(prompt,stage,detail)
 }
 private fun emit(prompt:String,stage:AudioStage,detail:String?=null) {
  val event=AudioPlaybackEvent(prompt,stage,detail);lastEvent=event;runCatching{onEvent(event)}
 }
}