package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.SupportCredentialProperties;
import com.hm.manage.service.support.CredentialCryptoService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceLocalService.*;

/** Real bounded engine + encrypted filesystem store; no database, worker process or remote service. */
class DataGovernanceLocalServiceTest {
    @TempDir Path temporary;
    DataGovernanceLocalProperties properties;
    DataGovernanceLocalStore store;
    DataGovernanceLocalService service;
    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void open() throws Exception {
        properties = new DataGovernanceLocalProperties(); properties.setStorageDir(temporary.toRealPath().resolve("private-local-flow").toString());
        reopen();
    }
    void reopen() throws Exception {
        store = new DataGovernanceLocalStore(properties, crypto());
        service = new DataGovernanceLocalService(store, properties, new DataGovernanceLocalEngine());
    }
    static CredentialCryptoService crypto() throws Exception {
        SupportCredentialProperties settings = new SupportCredentialProperties(); settings.setKey("synthetic-local-flow-test-key-not-production");
        CredentialCryptoService crypto = new CredentialCryptoService(); var field = CredentialCryptoService.class.getDeclaredField("properties"); field.setAccessible(true); field.set(crypto, settings); return crypto;
    }
    @AfterEach void close() { if (service != null) service.close(); if (store != null) store.close(); }
    static Map<String,Object> map(Object... entries) { Map<String,Object> result = new LinkedHashMap<>(); for (int i=0;i<entries.length;i+=2) result.put((String)entries[i],entries[i+1]); return result; }
    static Map<String,Object> node(String id,String type,Map<String,Object> config) { return map("id",id,"name",id,"type",type,"position",map("x",1,"y",2),"config",config); }
    static Map<String,Object> graph(String city) {
        List<Map<String,Object>> nodes = List.of(
            node("input","CSV_INPUT",map("content","name,city,amount\n bob ,上海,30\n alice ,苏州,10\n 张三 ,苏州,20\n","delimiter",",","header",true)),
            node("map","FIELD_MAP",map("fields",List.of(map("source","name","target","姓名","trim",true),map("source","city","target","城市","trim",true),map("source","amount","target","金额","trim",true)),"keepUnmapped",false)),
            node("filter","FILTER",map("field","城市","operator","EQ","value",city,"valueType","STRING")),
            node("sort","SORT",map("fields",List.of(map("field","金额","direction","DESC","valueType","NUMBER")))),
            node("output","OUTPUT",map("format","CSV","filename","result.csv")));
        List<Map<String,Object>> edges = new ArrayList<>();
        for(int i=1;i<nodes.size();i++) edges.add(map("id","edge-"+i,"sourceId",nodes.get(i-1).get("id"),"targetId",nodes.get(i).get("id")));
        return map("nodes",nodes,"connections",edges);
    }
    String save(Map<String,Object> graph) { return (String)service.save(null,map("name","本地合成流程","graph",graph),7).get("id"); }
    static Map<String,Object> request(long revision,String mode,String key) {
        Map<String,Object> body=map("revision",revision,"mode",mode,"sampleLimit",1,"requestId",key); if(mode.equals("preview"))body.put("previewNodeId","sort"); return body;
    }
    Map<String,Object> complete(String id) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15); Map<String,Object> result;
        do { result=service.run(id,7); if(Boolean.TRUE.equals(result.get("finalized"))) return result; Thread.sleep(5); } while(System.nanoTime()<deadline);
        fail("Execution did not finalize: "+result); return null;
    }
    Map<String,Object> execute(String id,long revision,String mode,String key) throws Exception {
        Map<String,Object> submitted=service.submit(id,request(revision,mode,key),7); return complete((String)submitted.get("id"));
    }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Map<String,Object> object,String key) { return (List<Map<String,Object>>)object.get(key); }
    String output(Map<String,Object> run) {
        var file=rows(run,"outputs").get(0); var download=service.download((String)run.get("id"),(String)file.get("id"),7);
        assertEquals("result.csv",download.filename()); assertTrue(download.contentType().startsWith("text/csv"));
        assertEquals(((Number)file.get("bytes")).intValue(),download.bytes().length);
        return new String(download.bytes(),StandardCharsets.UTF_8);
    }

    @Test void ownerAndRevisionChecksProtectDefinitionsAndCallerObjectsCannotMutatePersistedGraphs() {
        Map<String,Object> original=graph("苏州"); String id=save(original);
        original.put("nodes",List.of());
        assertEquals(5,((List<?>)((Map<?,?>)service.definition(id,7).get("graph")).get("nodes")).size());
        assertTrue(service.definitions(8).isEmpty());
        assertThrows(ServiceException.class,()->service.definition(id,8));
        assertThrows(ServiceException.class,()->service.validate(id,8));
        assertThrows(ServiceException.class,()->service.save(id,map("name","other-owner","revision",1,"graph",graph("上海")),8));
        assertThrows(ServiceException.class,()->service.save(id,map("name","stale","revision",0,"graph",graph("上海")),7));
        assertEquals(1L,service.definition(id,7).get("revision"));
        assertEquals(2L,service.save(id,map("name","updated","revision",1,"graph",graph("上海")),7).get("revision"));
    }

    @Test void previewProducesSamplesWithoutFilesAndFullExecutionWritesAllRowsBeyondSampleLimit() throws Exception {
        String id=save(graph("苏州")); assertEquals(true,service.validate(id,7).get("valid"));
        var preview=execute(id,1,"preview","preview-once"); assertEquals("SUCCEEDED",preview.get("state"));
        assertEquals(2,preview.get("outputRows")); assertEquals(4,rows(preview,"steps").size()); assertTrue(rows(preview,"outputs").isEmpty());
        assertEquals(1,rows(rows(preview,"steps").get(3),"sampleRows").size());
        assertThrows(ServiceException.class,()->service.download((String)preview.get("id"),"missing",7));
        var full=execute(id,1,"run","full-once"); assertEquals("SUCCEEDED",full.get("state"));
        assertEquals("姓名,城市,金额\r\n张三,苏州,20\r\nalice,苏州,10\r\n",output(full));
        assertEquals(3,full.get("inputRows")); assertEquals(2,full.get("outputRows"));
        String runId=(String)full.get("id"), fileId=(String)rows(full,"outputs").get(0).get("id");
        assertThrows(ServiceException.class,()->service.run(runId,8));
        assertThrows(ServiceException.class,()->service.download(runId,fileId,8));
        assertThrows(ServiceException.class,()->service.runs(id,8));
    }

    @Test void editingTheGraphChangesRealOutputWhilePreviousRunAndFrozenPlanRetainOriginalVersion() throws Exception {
        String id=save(graph("苏州")); Definition frozen=service.capture(id,7);
        var original=execute(id,1,"run","original"); String originalBytes=output(original);
        service.save(id,map("name","edited","revision",1,"graph",graph("上海")),7);
        var edited=execute(id,2,"run","edited"); assertEquals("姓名,城市,金额\r\nbob,上海,30\r\n",output(edited));
        var scheduled=service.submitFrozen(id,7,frozen.revision,frozen.graph,"schedule-frozen-request");
        var completed=complete((String)scheduled.get("id")); assertEquals("SUCCEEDED",completed.get("state"));
        assertEquals(1L,completed.get("revision")); assertEquals(originalBytes,output(completed));
        assertEquals(originalBytes,output(service.run((String)original.get("id"),7)));
        assertEquals(2L,service.definition(id,7).get("revision"));
    }

    @Test void sameRequestIsIdempotentAcrossEditsAndDifferentArgumentsAreRejected() throws Exception {
        String id=save(graph("苏州")); var body=request(1,"run","fixed-request");
        Map<String,Object> first, repeated;
        synchronized(service) { first=service.submit(id,body,7); repeated=service.submit(id,body,7); }
        assertEquals(first.get("id"),repeated.get("id")); complete((String)first.get("id"));
        assertEquals(1,service.runs(id,7).size());
        assertEquals(first.get("id"),service.findRequest(id,"fixed-request",7).get("id"));
        assertNull(service.findRequest(id,"unknown-request",7));
        var changed=new LinkedHashMap<>(body);changed.put("sampleLimit",2);
        assertThrows(ServiceException.class,()->service.submit(id,changed,7));
        service.save(id,map("name","edited","revision",1,"graph",graph("上海")),7);
        assertEquals(first.get("id"),service.submit(id,body,7).get("id"));
        assertEquals(1,service.runs(id,7).size());
    }

    @Test void stopIsCooperativeAndFinalizedBeforeACompletedStoppedRunCanBeReused() throws Exception {
        String id=save(graph("苏州")); Map<String,Object> submitted,stop;
        synchronized(service) {
            submitted=service.submit(id,request(1,"run","stop-request"),7);
            stop=service.stop((String)submitted.get("id"),7);
            assertEquals("STOPPING",stop.get("state")); assertEquals(false,stop.get("finalized"));
        }
        var finished=complete((String)submitted.get("id")); assertEquals("STOPPED",finished.get("state"));
        assertTrue(rows(finished,"outputs").isEmpty());
        assertThrows(ServiceException.class,()->service.download((String)finished.get("id"),"any",7));
        assertEquals(finished.get("id"),service.submit(id,request(1,"run","stop-request"),7).get("id"));
        assertEquals(1,service.runs(id,7).size());
    }

    @Test void persistedUnfinishedRunBecomesInterruptedAfterRestartWithoutReplay() throws Exception {
        String id=save(graph("苏州")); var completed=execute(id,1,"run","restart-request"); String runId=(String)completed.get("id");
        StoredRun residue=store.read("runs",runId,StoredRun.class); residue.state="RUNNING";residue.finalized=false;residue.finishedAt=null;residue.outputs.clear();residue.outputContents.clear();
        store.write("runs",runId,residue); service.close();store.close();reopen();
        var restored=service.findRequest(id,"restart-request",7);
        assertEquals("INTERRUPTED",restored.get("state"));assertEquals(true,restored.get("finalized"));
        assertTrue(rows(restored,"outputs").isEmpty());assertNotNull(restored.get("finishedAt"));
        assertEquals(runId,service.submit(id,request(1,"run","restart-request"),7).get("id"));
        assertEquals("INTERRUPTED",service.run(runId,7).get("state"));assertEquals(1,service.runs(id,7).size());
    }

    @Test void definitionRunInputAndDownloadedContentAreEncryptedOnDiskWithPrivateFiles() throws Exception {
        String id=save(graph("苏州"));var run=execute(id,1,"run","encrypted-evidence");assertEquals("SUCCEEDED",run.get("state"));
        assertTrue(output(run).contains("张三"));
        try(var paths=Files.walk(Path.of(properties.getStorageDir()))) {
            for(Path path:paths.filter(Files::isRegularFile).toList()) {
                assertTrue(Files.getPosixFilePermissions(path).stream().noneMatch(value->value.name().startsWith("GROUP_")||value.name().startsWith("OTHERS_")),path.toString());
                String bytes=Files.readString(path);
                for(String plain:List.of(" bob "," alice ","苏州","张三","name,city,amount","姓名,城市,金额"))assertFalse(bytes.contains(plain),path+" contains cleartext "+plain);
                if(path.toString().endsWith(".json")){var envelope=mapper.readTree(bytes);assertEquals(1,envelope.path("schemaVersion").asInt());assertTrue(envelope.hasNonNull("payload"));assertFalse(envelope.has("graph"));}
            }
        }
    }
    @Test void initialRunningJournalFailureFinalizesAsFailedAndReleasesTheOwner() throws Exception {
        FailingStore injected=useFailingStore(); injected.failRunning.set(true);
        String id=save(graph("苏州")); var failed=execute(id,1,"run","journal-start-failure");
        assertEquals("FAILED",failed.get("state"));assertEquals(1,injected.runningFailures.get());assertTrue(rows(failed,"outputs").isEmpty());
        var recovered=execute(id,1,"run","after-start-failure");assertEquals("SUCCEEDED",recovered.get("state"));
        assertEquals("姓名,城市,金额\r\n张三,苏州,20\r\nalice,苏州,10\r\n",output(recovered));
    }

    @Test void terminalJournalFailureRetainsTheOwnerAndRetriesPersistenceWithoutReexecuting() throws Exception {
        FailingStore injected=useFailingStore();injected.failTerminal.set(true);
        String id=save(graph("苏州"));var submitted=service.submit(id,request(1,"run","terminal-failure"),7);String runId=(String)submitted.get("id");
        assertTrue(injected.terminalAttempt.await(5,TimeUnit.SECONDS));
        var waiting=service.run(runId,7);assertEquals("FINALIZING",waiting.get("state"));assertEquals(false,waiting.get("finalized"));assertTrue(rows(waiting,"outputs").isEmpty());
        assertThrows(ServiceException.class,()->service.download(runId,"unknown",7));
        var blocked=service.submit(id,request(1,"run","must-not-overlap"),7);assertEquals("FAILED",blocked.get("state"));
        injected.failTerminal.set(false);
        var persisted=service.run(runId,7);assertEquals("SUCCEEDED",persisted.get("state"));assertEquals(true,persisted.get("finalized"));
        assertEquals("姓名,城市,金额\r\n张三,苏州,20\r\nalice,苏州,10\r\n",output(persisted));
        assertEquals(runId,service.submit(id,request(1,"run","terminal-failure"),7).get("id"));
        assertEquals("SUCCEEDED",execute(id,1,"run","after-finalization").get("state"));
    }

    @Test void largeSingleRowAcrossManyNodesBoundsSamplesWithoutLosingCountsOrOutput() throws Exception {
        String payload="x".repeat(600*1024);List<Map<String,Object>> nodes=new ArrayList<>(),edges=new ArrayList<>();
        nodes.add(node("input","JSON_INPUT",map("content",mapper.writeValueAsString(List.of(map("payload",payload))))));
        for(int i=0;i<26;i++)nodes.add(node("map-"+i,"FIELD_MAP",map("fields",List.of(map("source","payload","target","payload","trim",false)),"keepUnmapped",false)));
        nodes.add(node("output","OUTPUT",map("format","CSV","filename","result.csv")));
        for(int i=1;i<nodes.size();i++)edges.add(map("id","edge-"+i,"sourceId",nodes.get(i-1).get("id"),"targetId",nodes.get(i).get("id")));
        String id=save(map("nodes",nodes,"connections",edges));var completed=execute(id,1,"run","large-single-row");
        assertEquals("SUCCEEDED",completed.get("state"));assertEquals(1,completed.get("inputRows"));assertEquals(1,completed.get("outputRows"));
        assertEquals(28,rows(completed,"steps").size());
        assertTrue(mapper.writeValueAsBytes(rows(completed,"steps")).length<512*1024+8192);
        assertTrue(rows(completed,"steps").stream().allMatch(step->((Number)step.get("outputRows")).intValue()==1));
        assertTrue(rows(completed,"steps").stream().anyMatch(step->Boolean.TRUE.equals(step.get("sampleTruncated"))));
        assertEquals("payload\r\n"+payload+"\r\n",output(completed));
    }

    @Test void stoppingWhileFinalizationIsPendingDiscardsUnpublishedOutput() throws Exception {
        FailingStore injected=useFailingStore();injected.failTerminal.set(true);
        String id=save(graph("苏州"));var submitted=service.submit(id,request(1,"run","stop-finalizing"),7);String runId=(String)submitted.get("id");
        assertTrue(injected.terminalAttempt.await(5,TimeUnit.SECONDS));
        assertEquals("FINALIZING",service.run(runId,7).get("state"));
        service.stop(runId,7);
        var stopped=service.run(runId,7);assertEquals("STOPPED",stopped.get("state"));assertEquals(true,stopped.get("finalized"));assertTrue(rows(stopped,"outputs").isEmpty());
        assertThrows(ServiceException.class,()->service.download(runId,"unpublished",7));
    }

    FailingStore useFailingStore() throws Exception {
        service.close();store.close();FailingStore injected=new FailingStore(properties,crypto());store=injected;
        service=new DataGovernanceLocalService(store,properties,new DataGovernanceLocalEngine());return injected;
    }
    static class FailingStore extends DataGovernanceLocalStore {
        final AtomicBoolean failRunning=new AtomicBoolean(),failTerminal=new AtomicBoolean();
        final AtomicInteger runningFailures=new AtomicInteger();final CountDownLatch terminalAttempt=new CountDownLatch(1);
        FailingStore(DataGovernanceLocalProperties properties,CredentialCryptoService crypto){super(properties,crypto);}
        @Override public synchronized void write(String kind,String id,Object value){
            if(value instanceof StoredRun run){
                if("RUNNING".equals(run.state)&&failRunning.compareAndSet(true,false)){runningFailures.incrementAndGet();throw new ServiceException("synthetic first RUNNING journal failure");}
                if(run.finalized&&"SUCCEEDED".equals(run.state)&&failTerminal.get()){terminalAttempt.countDown();throw new ServiceException("synthetic terminal journal failure");}
            }
            super.write(kind,id,value);
        }
    }

}
