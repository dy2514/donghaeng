package ai.pleos.coach.data
import ai.pleos.coach.core.*
import org.junit.Assert.*
import org.junit.Test
class StateCodecTest {
 @Test fun roundTripKeepsNullsUserChoicesAndAudioOutcome(){
  val p=Preference("school-right",true,Reason.FORGOT_SIGNAL,Goal.SIGNAL_CHECK,Assistance.BRIEF,Difficulty.HARD,"정문 앞 버스")
  val e=CoachEngine(listOf(p),Source.REPLAY,"test",1000)
  e.ingest(Snapshot(1000,Source.REPLAY,segmentId="school-right",approachId="school-eastbound",brake=null,signal=TurnSignal.UNKNOWN))
  val record=e.finish(1500).single().copy(difficulty=Difficulty.HARD,reason=Reason.TRAFFIC,note="교통 상황")
  val state=SavedState(listOf(p,Preference("gate-left")),listOf(record),mapOf(record.id to "음성 사용 불가"))
  assertEquals(state,StateCodec.decode(StateCodec.encode(state)))
  assertNull(StateCodec.decode(StateCodec.encode(state)).records.single().samples.single().speedKmh)
 }
 @Test fun retentionNeverCombinesLiveAndReplay(){
  val e=CoachEngine(emptyList(),Source.REPLAY,"r",1000);e.ingest(Snapshot(1000,Source.REPLAY,segmentId="school-right",approachId="school-eastbound"))
  val r=e.finish(1500).single()
  val retained=StateCodec.retainLatest(listOf(r.copy(id="r1",startedAtMs=1000),r.copy(id="r2",startedAtMs=2000),r.copy(id="r3",startedAtMs=3000),r.copy(id="live",source=Source.LIVE)))
  assertEquals(3,retained.size);assertTrue(retained.any{it.id=="live"});assertFalse(retained.any{it.id=="r1"})
 }
 @Test(expected=IllegalArgumentException::class) fun unsupportedSchemaRejected(){StateCodec.decode(StateCodec.encode(SavedState()).put("version",99))}
}
