package com.salesforce.cte.testadvisor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import com.github.romankh3.image.comparison.model.Rectangle;
import com.salesforce.cte.common.TestCaseExecution;
import com.salesforce.cte.common.TestEvent;
import com.salesforce.cte.datamodel.client.RectangleDeserializer;
import com.salesforce.cte.datamodel.client.RectangleSerializer;
import com.salesforce.cte.datamodel.client.TestRunSignal;
import com.salesforce.cte.datamodel.client.TestSignal;

/**
 * @author Yibing Tao
 * Registry class manages TestAdvisor registry, including TestAdvisor properties
 * list test runs and save test run signals.
 */
public class Registry {
    
    public static final String SIGNAL_FILENAME = "test-signal.json";
    public static final String PORTAL_RECORD_FILENAME = "test-result.record";
    public static final String TESTADVISOR_TESTRUN_PREFIX = "TestRun-";
    public static final String TESTADVISOR_TESTRUN_PATTERN_STRING = "yyyyMMdd-HHmmss";
    public static final String TESTADVISOR_PROPERTIES_FILENAME = "testadvisor.properties";
    public static final String TESTADVISOR_DEFAULT_REGISGRY = ".testadvisor"; //TODO: what about different platform
    public static final String TESTADVISOR_TEST_RESULT = "test-result.json";
    public static final String TESTADVISOR_PROPERTY_CLIENT_GUID = "ClientRegistryGuid";
     
    private Properties registryConfig = new Properties();
    private Path registryRoot;
    public Path getRegistryRoot(){
        return registryRoot;
    }

    public Registry() throws IOException{
        //get registry root
        if (System.getenv("TESTADVISOR") == null){
            //env not set
            registryRoot = Paths.get(System.getProperty("user.dir"))
                            .resolve(Paths.get(TESTADVISOR_DEFAULT_REGISGRY))
                            .toAbsolutePath();
        }
        else
            registryRoot = Paths.get(System.getenv("TESTADVISOR"));
        initRegistry();
    }

    public Registry(Path root) throws IOException{
        registryRoot = root;
        initRegistry();
    }

    /**
     * Init Registry
     * @throws IOException
     * This exception will be thrown when it fails to access registry configruation file
     */
    private void initRegistry() throws IOException {
        //create registry root folder first
        if (!registryRoot.toFile().exists())
            registryRoot.toFile().mkdirs();

        //create property file if necessary
        if (!registryRoot.resolve(TESTADVISOR_PROPERTIES_FILENAME).toFile().exists())
            createRegistryProperties();
    }

    /**
     * Get TestAdvisor registry configuration from its default properties file
     * @return
     * TestAdvisor registry configruation properties
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public Properties getRegistryProperties() throws IOException{
        try(InputStream input = Files.newInputStream(registryRoot.resolve(TESTADVISOR_PROPERTIES_FILENAME))){
            registryConfig.load(input);
        }
        return registryConfig;
    }

    /**
     * set and save registry properties
     * @param key
     * Property key
     * @param value
     * Property value
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public void saveRegistryProperty(String key, String value) throws IOException{
        registryConfig.setProperty(key, value);
        saveRegistryProperties();
    }

    /**
     * Save the TestAdvisor registry configruation properties
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public void saveRegistryProperties() throws IOException{
        //Generate a random UUID if not present yet
        String guid = registryConfig.getProperty(TESTADVISOR_PROPERTY_CLIENT_GUID,"");
        if ( guid.isEmpty())
            registryConfig.setProperty(TESTADVISOR_PROPERTY_CLIENT_GUID, UUID.randomUUID().toString());

        //Save all properites
        try(OutputStream output = Files.newOutputStream(registryRoot.resolve(TESTADVISOR_PROPERTIES_FILENAME))){
			registryConfig.store(output, null);
		}
    }

    /**
     * Save the test run signal to registry
     * @param testRunSignal
     * Test run signal object, including test run id
     * @return
     * test signal file name in registry
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public String saveTestRunSignal(TestRunSignal testRunSignal) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Rectangle.class, new RectangleSerializer());
        objectMapper.registerModule(module);
        String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(testRunSignal);
        //create test run folder if necessary
        registryRoot.resolve(testRunSignal.testRunId).toFile().mkdirs();
        String fileName = registryRoot.resolve(testRunSignal.testRunId).resolve(SIGNAL_FILENAME).toString();
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))){
          writer.write(content);
        }
        return fileName;
    }

    /**
     * Get a list of Path with start with TestRun and doesn't contain a test-signal file
     * @return
     * List of Path object represent test runs haven't process yet
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public List<Path> getUnprocessedTestRunList() throws IOException{
        
        List<Path> allTestRunList;
        try(Stream<Path> pathStream = Files.walk(registryRoot,1)){
            allTestRunList =  pathStream.filter(Files::isDirectory)
                                        .filter(path -> path.toString().contains(TESTADVISOR_TESTRUN_PREFIX))
                                        .collect(Collectors.toList());
        }
        List<Path> unProcessedTestRunList = new ArrayList<>();
        for(Path testRun : allTestRunList){
            try(Stream<Path> pathStream = Files.walk(testRun, 1)){
                if (pathStream.noneMatch(name -> name.endsWith(SIGNAL_FILENAME)))
                    unProcessedTestRunList.add(testRun);
            }
        }
        return unProcessedTestRunList;
    }

    /**
     * Get a list of test run as Path in TestAdvisor registry which is ready to upload
     * @return
     * list of test run which is ready for upload
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public List<Path> getReadyToUploadTestRunList() throws IOException{
        // get all test run from regitster
        List<Path> allTestRunList;
        try(Stream<Path> pathStream = Files.walk(registryRoot,1)){
            allTestRunList =  pathStream.filter(Files::isDirectory)
                                        .filter(path -> path.toString().contains(TESTADVISOR_TESTRUN_PREFIX))
                                        .collect(Collectors.toList());
        }
        // filter test run with signal file
        List<Path> readyList = new ArrayList<>();
        for(Path testRun : allTestRunList){
            try(Stream<Path> pathStream = Files.walk(testRun, 1)){
                if (pathStream.anyMatch(name -> name.endsWith(SIGNAL_FILENAME)))
                    readyList.add(testRun);
            }
        }
        // filter test run without record file (not upload yet)
        List<Path> uploadList = new ArrayList<>();
        for(Path testRun : readyList){
            try(Stream<Path> pathStream = Files.walk(testRun, 1)){
                if (pathStream.noneMatch(name -> name.endsWith(PORTAL_RECORD_FILENAME)))
                    uploadList.add(testRun.resolve(SIGNAL_FILENAME));
            }
        }

        return uploadList;
    }

    /**
     * Get test run signal object for current test run from registry
     * @param path
     * Path to current test run
     * @return
     * Test run singal object
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     * @throws JsonMappingException
     * This exception is thrown when it failed to parse test result json 
     * @throws JsonParseException
     * This exception is thrown when it failed to parse test result json
     */
    public TestRunSignal getTestRunSignal(Path path) throws IOException{
        String fileName = path.getParent().resolve(SIGNAL_FILENAME).toAbsolutePath().toString();
        
        try(InputStream is = new FileInputStream(fileName)){
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            SimpleModule module = new SimpleModule();
            module.addDeserializer(Rectangle.class, new RectangleDeserializer());
            objectMapper.registerModule(module);
            return objectMapper.readValue(is, TestRunSignal.class);
        }
    }

    /**
     * Save current test run response to registry
     * @param path
     * Path to current test run
     * @param response
     * Portal response for signal upload
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    public void savePortalResponse(Path path, String response) throws IOException{
        String filename = path.getParent().resolve(PORTAL_RECORD_FILENAME).toAbsolutePath().toString();
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filename))){
            writer.write(response);
        }
    }

    /**
     * Extract the test run id from path based on TestAdvisor registry specification
     * @param path
     * Path object point to a test run
     * @return
     * Test run id if Path object contains a test run id, otherwise return an empty 
     * string
     */
    public String getTestRunId(Path path){
        return getTestRunId(path.toAbsolutePath().toString());
    }

    /**
     * Extract the test run id from path based on TestAdvisor registry specification
     * @param path
     * String of path to a test run
     * @return
     * Test run id if Path object contains a test run id, otherwise return test run id
     * based on current local time
     */
    public String getTestRunId(String path){
        DateTimeFormatter taDateFormatter = DateTimeFormatter.ofPattern(TESTADVISOR_TESTRUN_PATTERN_STRING);
        String testRunId = TESTADVISOR_TESTRUN_PREFIX + taDateFormatter.format(OffsetDateTime.now( ZoneOffset.UTC ));
        Pattern pattern = Pattern.compile(".*(TestRun-\\d{8}-\\d{6}).*");
        Matcher matcher = pattern.matcher(path);
        return matcher.find( ) ? matcher.group(0) : testRunId;
    }

    /**
     * Get the TestAdvisor test result file in registry
     * @param testRun
     * Path to test run
     * @return
     * Path object of TestAdvisor test result file
     * or null if no test result file found
     * @throws IOException
     * Throws IOException when failed to access test run folder or files
     */
    public Path getTestAdvisorTestResultFile(Path testRun) throws IOException{
        try(Stream<Path> pathStream = Files.walk(testRun,1)){
            return pathStream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(TESTADVISOR_TEST_RESULT))
                            .findFirst()
                            .orElse(null);                                     
        }
    }

    /**
     * create a new set of empty registry properties and save it
     * @throws IOException
     * This exception is thrown when it failed to access registry properties
     */
    private void createRegistryProperties() throws IOException{
        registryConfig.clear();
        registryConfig.put("SandboxInstance", "");
        registryConfig.put("SandboxOrgName", "");
        registryConfig.put("SandboxOrgId", "");
        registryConfig.put("TestSuiteName", "");
        registryConfig.put("auth.url", "https://test.salesforce.com");
        registryConfig.put("portal.clientid", "clientid");
        registryConfig.put("portal.url", "");
        registryConfig.put("portal.token.encrypted", "no");
        registryConfig.put("portal.accesstoken", "");
        registryConfig.put("portal.refreshtoken", "");

        saveRegistryProperties();
    }

    /**
     * Compare test advisor test case execution result based on screenshots and collect signals
     * @param baseline baseline test case execution
     * @param current current test case execution
     * @return
     */
    public int compareTestAdvisorTestCaseExecution(TestCaseExecution baseline, TestCaseExecution current, List<TestSignal> signalList){
        signalList.clear();
        // sort event list by event time, oldest first
        Comparator<TestEvent> compareByEventTime = Comparator.comparing(TestEvent::getEventTime);
        baseline.eventList.sort(compareByEventTime);
        current.eventList.sort(compareByEventTime);

        // get test step list by unique screenshots
        List<TestEvent> baselineSteps = getTestStepListByUniqueScreenshots(baseline.eventList);
        List<TestEvent> currentSteps = getTestStepListByUniqueScreenshots(current.eventList);

        int i=0; //current test step index
        int j=0; //baseline test step index
        int matchCount=0;
        
        List<TestSignal> subSignalList = new ArrayList<>();
        TestEvent prevStep = null;
        //for every event in current test
        for(TestEvent event : current.eventList){
            TestEvent baselineStep = baselineSteps.get(j);
            TestEvent currentStep = currentSteps.get(i);
            
            if (event == currentStep){
                //current event is a test step
                if(isMatchScreenshotEvent(currentStep, baselineStep)){
                    // find a match baseline step
                    matchCount++;
                    ImageComparisonResult result = ScreenshotManager.screenshotsComparison(
                        baselineStep.getScreenshotPath(),currentStep.getScreenshotPath());
                    
                    if (result.getImageComparisonState() == ImageComparisonState.MISMATCH 
                        || result.getDifferencePercent() > 25.0f ){
                        //image comparison found diff
                        TestSignal signal = createTestSignalFromEvent(event);
                        signal.subSignalList = subSignalList;
                        signal.screenshotDiffRatio = (int) result.getDifferencePercent();
                        signal.baselinScreenshotRecorderNumber = baselineStep.getScreenshotRecordNumber();
                        signal.screenshotDiffAreas = result.getRectangles();
                        signal.previousSignalTime = prevStep == null ?  null : prevStep.getEventTime();
                        signalList.add(signal);
                    }
                    prevStep = currentStep;
                    subSignalList = new ArrayList<>();
                    i++;
                }
                // looking for next baseline step
                j++;
            }
            else{
                subSignalList.add(createTestSignalFromEvent(event));
            }
        }

        return  (int)(((float)matchCount)/currentSteps.size() * 100);
    }

    private TestSignal createTestSignalFromEvent(TestEvent event){
        TestSignal signal = new TestSignal();
        signal.signalName = event.getEventSource();
        signal.signalValue = event.getEventContent();
        signal.signalTime = event.getEventTime();
        signal.screenshotRecorderNumber = event.getScreenshotRecordNumber();
        signal.locatorHash = getMD5Hash(event.getSeleniumLocator());
        signal.seleniumCmd = event.getSeleniumCmd();
        return signal;
    }
    /**
     * Get list of screenshot events for event list which represent the test steps
     * add all other events as 
     * @param testEventList list of test events order by event time
     * @return list of screenshot events
     */
    private List<TestEvent> getTestStepListByUniqueScreenshots(List<TestEvent> testEventList){
        List<TestEvent> testSteps = new ArrayList<>();
        TestEvent prevStep = null;
        for(TestEvent event : testEventList){
            if (event.screenshotPath == null 
                || event.screenshotPath.isEmpty()
                || isSameTypeScreenshotEvent(event, prevStep)) continue;
            testSteps.add(event);
            prevStep=event;
        }
        return testSteps;
    }   

    /**
     * Check whether 2 screenshot event consider same type
     * @param event1 first event
     * @param event2 second event
     * @return
     * true if both event consider same type
     * false otherwise
     */
    private boolean isSameTypeScreenshotEvent(TestEvent event1, TestEvent event2){
        // null event always consider different
        if(event1 == null || event2 == null)
             return false;

        return event1.getEventSource().equals(event2.getEventSource()) 
                && event1.getEventLevel().equals(event2.getEventLevel())
                && event1.getSeleniumCmd().equals(event2.getSeleniumCmd())
                && event1.getSeleniumCmdParam().equals(event2.getSeleniumCmdParam())
                && event1.getSeleniumLocator().equals(event2.getSeleniumLocator());
    }

    private boolean isMatchScreenshotEvent(TestEvent event1, TestEvent event2){
        if(event1 == null || event2 == null)
             return false;
        return event1.getSeleniumCmd().equals(event2.getSeleniumCmd())
                && event1.getSeleniumCmdParam().equals(event2.getSeleniumCmdParam())
                && event1.getSeleniumLocator().equals(event2.getSeleniumLocator());
    }

    private String getMD5Hash(String s) {
        if (s==null || s.isEmpty()) 
            return "";

        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
        messageDigest.update(s.getBytes());
        return new String(messageDigest.digest());
    }
}
