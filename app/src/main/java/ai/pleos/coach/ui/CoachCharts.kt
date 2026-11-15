package ai.pleos.coach.ui
import ai.pleos.coach.R
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import java.util.Locale
import kotlin.math.max

@Composable internal fun TurnSymbol(direction:String,color:Color,modifier:Modifier=Modifier){
 Canvas(modifier){
  val w=size.width;val h=size.height
  scale(scaleX=if(direction=="LEFT")-1f else 1f,scaleY=1f){
   val p=Path().apply{moveTo(.28f*w,.88f*h);lineTo(.28f*w,.45f*h);quadraticBezierTo(.28f*w,.27f*h,.46f*w,.27f*h);lineTo(.82f*w,.27f*h)}
   drawPath(p,color,style=Stroke(width=.09f*w,cap=StrokeCap.Square))
   drawLine(color,Offset(.61f*w,.07f*h),Offset(.82f*w,.27f*h),.09f*w,StrokeCap.Square)
   drawLine(color,Offset(.82f*w,.27f*h),Offset(.61f*w,.48f*h),.09f*w,StrokeCap.Square)
  }
 }
}
@Composable internal fun RouteSketch(segments:List<UiSegment>,modifier:Modifier=Modifier){
 val c=LocalCoachColors.current
 val font=ResourcesCompat.getFont(LocalContext.current,R.font.asta_sans)?.let{android.graphics.Typeface.create(it,400,false)}
 Canvas(modifier.semantics{contentDescription="연습 구간 도식. "+segments.joinToString(". "){it.title+". "+if(it.settings.selected)"연습 선택"else"기록만"}}){
  val w=size.width;val h=size.height
  val road=Path().apply{moveTo(.12f*w,.86f*h);lineTo(.12f*w,.56f*h);quadraticBezierTo(.12f*w,.44f*h,.2f*w,.44f*h);lineTo(.76f*w,.44f*h);quadraticBezierTo(.84f*w,.44f*h,.84f*w,.32f*h);lineTo(.84f*w,.18f*h)}
  drawPath(road,c.line,style=Stroke(width=24.dp.toPx(),cap=StrokeCap.Butt))
  drawPath(road,c.accent,style=Stroke(width=5.dp.toPx(),cap=StrokeCap.Butt))
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=font;textSize=24.sp.toPx();textAlign=Paint.Align.CENTER;color=c.text.toArgb()}
  listOf(Offset(.22f*w,.44f*h),Offset(.72f*w,.44f*h)).forEachIndexed{i,p->
   drawRect(c.surface,Offset(p.x-27.dp.toPx(),p.y-27.dp.toPx()),Size(54.dp.toPx(),54.dp.toPx()))
   drawRect(c.accent,Offset(p.x-27.dp.toPx(),p.y-27.dp.toPx()),Size(54.dp.toPx(),54.dp.toPx()),style=Stroke(2.dp.toPx()))
   paint.color=c.accent.toArgb();drawContext.canvas.nativeCanvas.drawText(if(i==0)"01"else"02",p.x,p.y+8.sp.toPx(),paint)
  }
  paint.color=c.secondary.toArgb();paint.textSize=22.sp.toPx()
  drawContext.canvas.nativeCanvas.drawText("우회전",.22f*w,.78f*h,paint)
  drawContext.canvas.nativeCanvas.drawText("좌회전",.72f*w,.78f*h,paint)
 }
}
@Composable internal fun SpeedPlot(record:UiRecord,maxTime:Float,maxSpeed:Float,modifier:Modifier=Modifier){
 val c=LocalCoachColors.current
 val font=ResourcesCompat.getFont(LocalContext.current,R.font.asta_sans)?.let{android.graphics.Typeface.create(it,400,false)}
 Canvas(modifier.semantics{contentDescription=record.title+". "+record.sourceLabel+". "+record.samples.size+"개 관찰. "+record.decisionLabel+". 속도 누락은 끊어진 선, 조작 불명은 물음표로 표시."}){
  val left=100.dp.toPx();val right=size.width-12.dp.toPx();val top=32.dp.toPx();val bottom=size.height*.52f
  val width=(right-left).coerceAtLeast(1f);val height=(bottom-top).coerceAtLeast(1f)
  fun x(t:Float)=left+width*(t/maxTime.coerceAtLeast(1f)).coerceIn(0f,1f)
  fun y(v:Float)=bottom-height*(v/maxSpeed.coerceAtLeast(1f)).coerceIn(0f,1f)
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=font;color=c.secondary.toArgb();textSize=20.sp.toPx()}
  listOf(0f,.5f,1f).forEach{ratio->
   val gy=bottom-height*ratio;drawLine(c.line,Offset(left,gy),Offset(right,gy),1.dp.toPx())
   paint.textAlign=Paint.Align.RIGHT;drawContext.canvas.nativeCanvas.drawText((ratio*maxSpeed).toInt().toString(),left-12.dp.toPx(),gy+7.sp.toPx(),paint)
  }
  paint.textAlign=Paint.Align.LEFT;drawContext.canvas.nativeCanvas.drawText("km/h",left,18.sp.toPx(),paint)
  val samples=record.samples.sortedBy{it.elapsedSeconds}
  var path=Path();var connected=false;var previousTime:Float?=null
  fun flush(){if(connected)drawPath(path,c.accent,style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round));path=Path();connected=false}
  for(s in samples){
   val speed=s.speedKmh
   if(speed==null||!speed.isFinite()||speed<0){flush();previousTime=null;continue}
   if(previousTime!=null&&s.elapsedSeconds-previousTime!!>3f)flush()
   val p=Offset(x(s.elapsedSeconds),y(speed))
   if(connected)path.lineTo(p.x,p.y)else{path.moveTo(p.x,p.y);connected=true}
   drawCircle(c.accent,2.dp.toPx(),p);previousTime=s.elapsedSeconds
  }
  flush()
  paint.color=c.secondary.toArgb()
  listOf(0f,.5f,1f).forEach{ratio->
   paint.textAlign=when(ratio){0f->Paint.Align.LEFT;1f->Paint.Align.RIGHT;else->Paint.Align.CENTER}
   drawContext.canvas.nativeCanvas.drawText(String.format(Locale.KOREA,"%.0f초",maxTime*ratio),left+width*ratio,bottom+32.dp.toPx(),paint)
  }
  fun lane(label:String,at:Float,pedal:Boolean,value:(UiSample)->String){
   val barH=32.dp.toPx()
   translate(0f,at-2.dp.toPx()){
    val iconSize=Size(34.dp.toPx(),34.dp.toPx())
    if(pedal)drawBrakePedal(iconSize,0f,true,c)
    else drawSignalArrow(iconSize,true,true,true,1f,c)
   }
   paint.color=c.secondary.toArgb();paint.textAlign=Paint.Align.RIGHT
   drawContext.canvas.nativeCanvas.drawText(label,left-12.dp.toPx(),at+23.dp.toPx(),paint)
   drawRect(c.background,Offset(left,at),Size(width,barH))
   var i=0
   while(i<samples.size){
    val first=i;val v=value(samples[i])
    while(i+1<samples.size && value(samples[i+1])==v && samples[i+1].elapsedSeconds-samples[i].elapsedSeconds<=3f)i++
    val end=minOf(samples.getOrNull(i+1)?.elapsedSeconds?:record.durationSeconds,samples[i].elapsedSeconds+3f)
    val bx=x(samples[first].elapsedSeconds);val bw=max(1.dp.toPx(),x(end)-bx)
    val active=v=="눌림"||v=="좌"||v=="우"
    drawRect(if(active)c.selected else c.background,Offset(bx,at),Size(bw,barH))
    drawRect(c.line,Offset(bx,at),Size(bw,barH),style=Stroke(1.dp.toPx()))
    if(bw>28.dp.toPx()){
     val showIcon=bw>76.dp.toPx() && v!="?"
     val icon=26.dp.toPx()
     if(showIcon)translate(bx+bw/2-34.dp.toPx(),at+3.dp.toPx()){
      if(pedal)drawBrakePedal(Size(icon,icon),if(v=="눌림")1f else 0f,true,c)
      else if(v=="좌"||v=="우")drawSignalArrow(Size(icon,icon),v=="좌",true,true,1f,c)
     }
     paint.textAlign=Paint.Align.CENTER;paint.color=if(active)(if(pedal)c.accent else signalColor(c)).toArgb()else c.secondary.toArgb();paint.textSize=18.sp.toPx()
     val shift=if(showIcon&&(pedal||active))14.dp.toPx()else 0f
     drawContext.canvas.nativeCanvas.drawText(v,bx+bw/2+shift,at+23.dp.toPx(),paint);paint.textSize=20.sp.toPx()
    }
    i++
   }
  }
  lane("페달",size.height*.73f,true){when(it.brake){true->"눌림";false->"해제";null->"?"}}
  lane("신호",size.height*.88f,false){when(it.signal){"LEFT"->"좌";"RIGHT"->"우";"OFF"->"꺼짐";else->"?"}}
 }
}
