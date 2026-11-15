package ai.pleos.coach.platform
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal class CarMonitor(private val context:Context,private val onSpeed:(Double?,Long)->Unit,private val onEditingAllowed:(Boolean)->Unit,private val onError:(String,String)->Unit){
 private val handler=Handler(Looper.getMainLooper())
 private var running=false;private var car:Car?=null
 private var properties:CarPropertyManager?=null
 private var restrictions:CarUxRestrictionsManager?=null
 private var parked:Boolean?=null;private var uxAllows=false
 private val callback=object:CarPropertyManager.CarPropertyEventCallback{
  override fun onChangeEvent(value:CarPropertyValue<*>){
   if(!running)return
   when(value.propertyId){
    VehiclePropertyIds.PERF_VEHICLE_SPEED->{
     val raw=value.value as? Float;val age=(SystemClock.elapsedRealtimeNanos()-value.timestamp)/1_000_000
     if(value.status==CarPropertyValue.STATUS_AVAILABLE&&raw!=null&&raw.isFinite()&&value.timestamp>0&&age in 0..2500)
      onSpeed(raw.toDouble()*3.6,System.currentTimeMillis()-age) else onSpeed(null,0)
    }
    VehiclePropertyIds.GEAR_SELECTION->{parked=if(value.status==CarPropertyValue.STATUS_AVAILABLE)(value.value as? Int)?.let{it==VehicleGear.GEAR_PARK}else null;publish()}
   }
  }
  override fun onErrorEvent(propertyId:Int,areaId:Int){
   if(!running)return
   if(propertyId==VehiclePropertyIds.PERF_VEHICLE_SPEED){onSpeed(null,0);onError("speed","차량 속도 이벤트 오류")}
   else if(propertyId==VehiclePropertyIds.GEAR_SELECTION){parked=null;publish();onError("ux","P 기어 확인 불가 · 편집 잠금")}
  }
 }
 private val uxListener=CarUxRestrictionsManager.OnUxRestrictionsChangedListener {if(running)accept(it)}
 fun start(){
  if(running)return
  running=true;onEditingAllowed(false)
  car=Car.createCar(context,handler,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT){connected,ready->
   if(!running)connected.disconnect()
   else if(ready){car=connected;connect(connected)}
   else{properties=null;restrictions=null;parked=null;uxAllows=false;onSpeed(null,0);publish();onError("ux","차량 연결 끊김 · 편집 잠금")}
  }
 }
 private fun connect(connected:Car){
  runCatching{properties?.unregisterCallback(callback)};runCatching{restrictions?.unregisterListener()}
  parked=null;uxAllows=false;publish()
  try{properties=connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager}
  catch(e:Exception){onError("speed",e.message.orEmpty());onError("ux","기어 정보 확인 불가")}
  try{if(properties?.registerCallback(callback,VehiclePropertyIds.PERF_VEHICLE_SPEED,2f)!=true){onSpeed(null,0);onError("speed","속도 구독 불가 · CAR_SPEED 권한 확인")}}
  catch(e:Exception){onSpeed(null,0);onError("speed",e.message.orEmpty())}
  try{if(properties?.registerCallback(callback,VehiclePropertyIds.GEAR_SELECTION,CarPropertyManager.SENSOR_RATE_ONCHANGE)!=true){parked=null;publish();onError("ux","기어 구독 불가 · CAR_POWERTRAIN 권한 확인")}}
  catch(e:Exception){parked=null;publish();onError("ux",e.message.orEmpty())}
  try{restrictions=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager;restrictions?.registerListener(uxListener);restrictions?.currentCarUxRestrictions?.let(::accept)?:onError("ux","조작 허용 상태 확인 불가")}
  catch(e:Exception){uxAllows=false;publish();onError("ux",e.message.orEmpty())}
 }
 private fun accept(value:CarUxRestrictions){uxAllows=!value.isRequiresDistractionOptimization&&value.activeRestrictions==CarUxRestrictions.UX_RESTRICTIONS_BASELINE;publish()}
 private fun publish(){onEditingAllowed(running&&parked==true&&uxAllows)}
 fun stop(){if(!running)return;running=false;runCatching{properties?.unregisterCallback(callback)};runCatching{restrictions?.unregisterListener()};runCatching{car?.disconnect()};properties=null;restrictions=null;car=null;parked=null;uxAllows=false;onEditingAllowed(false);onSpeed(null,0)}
}
