/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog;

import com.mongodb.BasicDBObject;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opencb.commons.test.GenericTest;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.datastore.core.config.DataStoreServerAddress;
import org.opencb.datastore.mongodb.MongoDataStore;
import org.opencb.datastore.mongodb.MongoDataStoreManager;
import org.opencb.opencga.catalog.db.api.CatalogFileDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManager;
import org.opencb.opencga.catalog.utils.CatalogFileUtils;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.catalog.models.File;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogIOException;
import org.opencb.opencga.core.common.StringUtils;
import org.opencb.opencga.core.common.TimeUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CatalogManagerTest extends GenericTest {

    public final String PASSWORD = "asdf";
    CatalogManager catalogManager;
    protected String sessionIdUser;
    protected String sessionIdUser2;
    protected String sessionIdUser3;

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private File testFolder;

    @Before
    public void setUp() throws IOException, CatalogException {
        InputStream is = CatalogManagerTest.class.getClassLoader().getResourceAsStream("catalog.properties");
        Properties properties = new Properties();
        properties.load(is);

        clearCatalog(properties);

        catalogManager = new CatalogManager(properties);

        catalogManager.createUser("user", "User Name", "mail@ebi.ac.uk", PASSWORD, "", null);
        catalogManager.createUser("user2", "User2 Name", "mail2@ebi.ac.uk", PASSWORD, "", null);
        catalogManager.createUser("user3", "User3 Name", "user.2@e.mail", PASSWORD, "ACME", null);

        sessionIdUser  = catalogManager.login("user",  PASSWORD, "127.0.0.1").first().getString("sessionId");
        sessionIdUser2 = catalogManager.login("user2", PASSWORD, "127.0.0.1").first().getString("sessionId");
        sessionIdUser3 = catalogManager.login("user3", PASSWORD, "127.0.0.1").first().getString("sessionId");

        Project project1 = catalogManager.createProject("user", "Project about some genomes", "1000G", "", "ACME", null, sessionIdUser).first();
        Project project2 = catalogManager.createProject("user2", "Project Management Project", "pmp", "life art intelligent system", "myorg", null, sessionIdUser2).first();
        Project project3 = catalogManager.createProject("user3", "project 1", "p1", "", "", null, sessionIdUser3).first();

        int studyId = catalogManager.createStudy(project1.getId(), "Phase 1", "phase1", Study.Type.TRIO, "Done", sessionIdUser).first().getId();
        int studyId2 = catalogManager.createStudy(project1.getId(), "Phase 3", "phase3", Study.Type.CASE_CONTROL, "d", sessionIdUser).first().getId();
        int studyId3 = catalogManager.createStudy(project2.getId(), "Study 1", "s1", Study.Type.CONTROL_SET, "", sessionIdUser2).first().getId();

        catalogManager.createFolder(studyId2, Paths.get("data/test/folder/"), true, null, sessionIdUser);


        testFolder = catalogManager.createFolder(studyId, Paths.get("data/test/folder/"), true, null, sessionIdUser).first();
        ObjectMap attributes = new ObjectMap();
        attributes.put("field", "value");
        attributes.put("numValue", 5);
        catalogManager.modifyFile(testFolder.getId(), new ObjectMap("attributes", attributes), sessionIdUser);

        File fileTest1k = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE,
                testFolder.getPath() + "test_1K.txt.gz",
                StringUtils.randomString(1000).getBytes(), "", false, sessionIdUser).first();
        attributes = new ObjectMap();
        attributes.put("field", "value");
        attributes.put("name", "fileTest1k");
        attributes.put("numValue", "10");
        attributes.put("boolean", false);
        catalogManager.modifyFile(fileTest1k.getId(), new ObjectMap("attributes", attributes), sessionIdUser);

        File fileTest05k = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE,
                testFolder.getPath() + "test_0.5K.txt",
                StringUtils.randomString(500).getBytes(), "", false, sessionIdUser).first();
        attributes = new ObjectMap();
        attributes.put("field", "valuable");
        attributes.put("name", "fileTest05k");
        attributes.put("numValue", 5);
        attributes.put("boolean", true);
        catalogManager.modifyFile(fileTest05k.getId(), new ObjectMap("attributes", attributes), sessionIdUser);

        File test01k = catalogManager.createFile(studyId, File.Format.IMAGE, File.Bioformat.NONE,
                testFolder.getPath() + "test_0.1K.png",
                StringUtils.randomString(100).getBytes(), "", false, sessionIdUser).first();
        attributes = new ObjectMap();
        attributes.put("field", "other");
        attributes.put("name", "test01k");
        attributes.put("numValue", 50);
        catalogManager.modifyFile(test01k.getId(), new ObjectMap("sampleIds", Arrays.asList(1,2,3,4,5)), sessionIdUser);
        catalogManager.modifyFile(test01k.getId(), new ObjectMap("attributes", attributes), sessionIdUser);



    }


    @After
    public void tearDown() throws Exception {
        if(sessionIdUser != null) {
            catalogManager.logout("user", sessionIdUser);
        }
        if(sessionIdUser2 != null) {
            catalogManager.logout("user2", sessionIdUser2);
        }
        if(sessionIdUser3 != null) {
            catalogManager.logout("user3", sessionIdUser3);
        }
    }


    @Test
    public void testCreateExistingUser() throws Exception {
        thrown.expect(CatalogException.class);
        catalogManager.createUser("user", "User Name", "mail@ebi.ac.uk", PASSWORD, "", null);
    }

    @Test
    public void testLoginAsAnonymous() throws Exception {
        System.out.println(catalogManager.loginAsAnonymous("127.0.0.1"));
    }

    @Test
    public void testLogin() throws Exception {
        QueryResult<ObjectMap> queryResult = catalogManager.login("user", PASSWORD, "127.0.0.1");
        System.out.println(queryResult.first().toJson());

        thrown.expect(CatalogException.class);
        catalogManager.login("user", "fakePassword", "127.0.0.1");
//        fail("Expected 'wrong password' exception");
    }


    @Test
    public void testLogoutAnonymous() throws Exception {
        QueryResult<ObjectMap> queryResult = catalogManager.loginAsAnonymous("127.0.0.1");
        catalogManager.logoutAnonymous(queryResult.first().getString("sessionId"));
    }

    @Test
    public void testGetUserInfo() throws CatalogException {
        QueryResult<User> user = catalogManager.getUser("user", null, sessionIdUser);
        System.out.println("user = " + user);
        QueryResult<User> userVoid = catalogManager.getUser("user", user.first().getLastActivity(), sessionIdUser);
        System.out.println("userVoid = " + userVoid);
        assertTrue(userVoid.getResult().isEmpty());
        try {
            catalogManager.getUser("user", null, sessionIdUser2);
            fail();
        } catch (CatalogException e) {
            System.out.println(e);
        }
    }

    @Test
    public void testModifyUser() throws CatalogException, InterruptedException {
        ObjectMap params = new ObjectMap();
        String newName = "Changed Name " + StringUtils.randomString(10);
        String newPassword = StringUtils.randomString(10);
        String newEmail = "new@email.ac.uk";

        params.put("name", newName);
        ObjectMap attributes = new ObjectMap("myBoolean", true);
        attributes.put("value", 6);
        attributes.put("object", new BasicDBObject("id", 1234));
        params.put("attributes", attributes);

        User userPre = catalogManager.getUser("user", null, sessionIdUser).first();
        System.out.println("userPre = " + userPre);
        Thread.sleep(10);

        catalogManager.modifyUser("user", params, sessionIdUser);
        catalogManager.changeEmail("user", newEmail, sessionIdUser);
        catalogManager.changePassword("user", PASSWORD, newPassword, sessionIdUser);

        List<User> userList = catalogManager.getUser("user", userPre.getLastActivity(), new QueryOptions("exclude", Arrays.asList("sessions")), sessionIdUser).getResult();
        if(userList.isEmpty()){
            fail("Error. LastActivity should have changed");
        }
        User userPost = userList.get(0);
        System.out.println("userPost = " + userPost);
        assertTrue(!userPre.getLastActivity().equals(userPost.getLastActivity()));
        assertEquals(userPost.getName(), newName);
        assertEquals(userPost.getEmail(), newEmail);
        assertEquals(userPost.getPassword(), newPassword);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            assertEquals(userPost.getAttributes().get(entry.getKey()), entry.getValue());
        }

        catalogManager.changePassword("user", newPassword, PASSWORD, sessionIdUser);

        try {
            params = new ObjectMap();
            params.put("password", "1234321");
            catalogManager.modifyUser("user", params, sessionIdUser);
            fail("Expected exception");
        } catch (CatalogDBException e){
            System.out.println(e);
        }

        try {
            catalogManager.modifyUser("user", params, sessionIdUser2);
            fail("Expected exception");
        } catch (CatalogException e){
            System.out.println(e);
        }

    }

    /**
     * Project methods
     * ***************************
     */


    @Test
    public void testCreateAnonymousProject() throws IOException, CatalogException {
        String sessionId = catalogManager.loginAsAnonymous("127.0.0.1").first().getString("sessionId");

        String userId = catalogManager.getUserIdBySessionId(sessionId);

        catalogManager.createProject(userId, "Project", "project", "", "", null, sessionId);

        catalogManager.logoutAnonymous(sessionId);

    }

    @Test
    public void testGetAllProjects() throws Exception {
        QueryResult<Project> projects = catalogManager.getAllProjects("user", null, sessionIdUser);
        assertEquals(1, projects.getNumResults());

        projects = catalogManager.getAllProjects("user", null, sessionIdUser2);
        assertEquals(0, projects.getNumResults());
    }

    @Test
    public void testCreateProject() throws Exception {

        String projectAlias = "projectAlias_ASDFASDF";

        catalogManager.createProject("user", "Project", projectAlias, "", "", null, sessionIdUser);

        thrown.expect(CatalogDBException.class);
        catalogManager.createProject("user", "Project", projectAlias, "", "", null, sessionIdUser);
    }

    @Test
    public void testModifyProject() throws CatalogException {
        String newProjectName = "ProjectName " + StringUtils.randomString(10);
        int projectId = catalogManager.getUser("user", null, sessionIdUser).first().getProjects().get(0).getId();

        ObjectMap options = new ObjectMap();
        options.put("name", newProjectName);
        ObjectMap attributes = new ObjectMap("myBoolean", true);
        attributes.put("value", 6);
        attributes.put("object", new BasicDBObject("id", 1234));
        options.put("attributes", attributes);

        catalogManager.modifyProject(projectId, options, sessionIdUser);
        QueryResult<Project> result = catalogManager.getProject(projectId, null, sessionIdUser);
        Project project = result.first();
        System.out.println(result);

        assertEquals(newProjectName, project.getName());
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            assertEquals(project.getAttributes().get(entry.getKey()), entry.getValue());
        }

        options = new ObjectMap();
        options.put("alias", "newProjectAlias");
        catalogManager.modifyProject(projectId, options, sessionIdUser);

        try {
            catalogManager.modifyProject(projectId, options, sessionIdUser2);
            fail("Expected 'Permission denied' exception");
        } catch (CatalogDBException e){
            System.out.println(e);
        }

    }

    /**
     * Study methods
     * ***************************
     */

    @Test
    public void testModifyStudy() throws Exception {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();
        String newName = "Phase 1 "+ StringUtils.randomString(20);
        String newDescription = StringUtils.randomString(500);

        ObjectMap parameters = new ObjectMap();
        parameters.put("name", newName);
        parameters.put("description", newDescription);
        BasicDBObject attributes = new BasicDBObject("key", "value");
        parameters.put("attributes", attributes);
        catalogManager.modifyStudy(studyId, parameters, sessionIdUser);

        QueryResult<Study> result = catalogManager.getStudy(studyId, sessionIdUser);
        System.out.println(result);
        Study study = result.first();
        assertEquals(study.getName(), newName);
        assertEquals(study.getDescription(), newDescription);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            assertEquals(study.getAttributes().get(entry.getKey()), entry.getValue());
        }
    }

    /**
     * File methods
     * ***************************
     */

    @Test
    public void testDeleteDataFromStudy() throws Exception {

    }

    @Test
    public void testCreateFolder() throws Exception {
        int projectId = catalogManager.getAllProjects("user2", null, sessionIdUser2).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser2).first().getId();

        Set<String> paths = catalogManager.getAllFiles(studyId, new QueryOptions("type", File.Type.FOLDER),
                sessionIdUser2).getResult().stream().map(File::getPath).collect(Collectors.toSet());
        assertEquals(3, paths.size());
        assertTrue(paths.contains(""));             //root
        assertTrue(paths.contains("data/"));        //data
        assertTrue(paths.contains("analysis/"));    //analysis

        Path folderPath = Paths.get("data", "new", "folder");
        File folder = catalogManager.createFolder(studyId, folderPath, true, null, sessionIdUser2).first();

        paths = catalogManager.getAllFiles(studyId, new QueryOptions("type", File.Type.FOLDER),
                sessionIdUser2).getResult().stream().map(File::getPath).collect(Collectors.toSet());
        assertEquals(5, paths.size());
        assertTrue(paths.contains("data/new/"));
        assertTrue(paths.contains("data/new/folder/"));
    }

    @Test
    public void testCreateAndUpload() throws Exception {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");
        int studyId2 = catalogManager.getStudyId("user@1000G:phase3");

        CatalogFileUtils catalogFileUtils = new CatalogFileUtils(catalogManager);

        java.io.File fileTest;

        String fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        QueryResult<File> fileResult = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.VARIANT, "data/" + fileName, "description", true, -1, sessionIdUser);

        fileTest = createDebugFile();
        catalogFileUtils.upload(fileTest.toURI(), fileResult.first(), null, sessionIdUser, false, false, true, true);
        assertTrue("File deleted", !fileTest.exists());

        fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        fileResult = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.VARIANT, "data/" + fileName, "description", true, -1, sessionIdUser);
        fileTest = createDebugFile();
        catalogFileUtils.upload(fileTest.toURI(), fileResult.first(), null, sessionIdUser, false, false, false, true);
        assertTrue("File don't deleted", fileTest.exists());
        assertTrue(fileTest.delete());

        fileName = "item." + TimeUtils.getTimeMillis() + ".txt";
        fileResult = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "data/" + fileName,
                StringUtils.randomString(200).getBytes(), "description", true, sessionIdUser);
        assertTrue("", fileResult.first().getStatus() == File.Status.READY);
        assertTrue("", fileResult.first().getDiskUsage() == 200);

        fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        fileTest = createDebugFile();
        QueryResult<File> fileQueryResult = catalogManager.createFile(
                studyId2, File.Format.PLAIN, File.Bioformat.VARIANT, "data/deletable/folder/" + fileName, "description", true, -1, sessionIdUser);
        catalogFileUtils.upload(fileTest.toURI(), fileQueryResult.first(), null, sessionIdUser, false, false, true, true);
        assertFalse("File deleted by the upload", fileTest.delete());

        fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        fileTest = createDebugFile();
        fileQueryResult = catalogManager.createFile(
                studyId2, File.Format.PLAIN, File.Bioformat.VARIANT, "data/deletable/" + fileName, "description", true, -1, sessionIdUser);
        catalogFileUtils.upload(fileTest.toURI(), fileQueryResult.first(), null, sessionIdUser, false, false, false, true);
        assertTrue(fileTest.delete());

        fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        fileTest = createDebugFile();
        fileQueryResult = catalogManager.createFile(
                studyId2, File.Format.PLAIN, File.Bioformat.VARIANT, "" + fileName, "file at root", true, -1, sessionIdUser);
        catalogFileUtils.upload(fileTest.toURI(), fileQueryResult.first(), null, sessionIdUser, false, false, false, true);
        assertTrue(fileTest.delete());

        fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        fileTest = createDebugFile();
        long size = Files.size(fileTest.toPath());
        fileQueryResult = catalogManager.createFile(studyId2, File.Format.PLAIN, File.Bioformat.VARIANT, "" + fileName,
                fileTest.toURI(), "file at root", true, sessionIdUser);
        assertTrue("File should be moved", !fileTest.exists());
        assertTrue(fileQueryResult.first().getDiskUsage() == size);
    }

    @Test
    public void testDownloadAndHeadFile() throws CatalogException, IOException, InterruptedException {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();
        CatalogFileUtils catalogFileUtils = new CatalogFileUtils(catalogManager);

        String fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        java.io.File fileTest;
        InputStream is = new FileInputStream(fileTest = createDebugFile());
        File file = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.VARIANT, "data/" + fileName, "description", true, -1, sessionIdUser).first();
        catalogFileUtils.upload(is, file, sessionIdUser, false, false, true);
        is.close();


        byte[] bytes = new byte[100];
        byte[] bytesOrig = new byte[100];
        DataInputStream fis = new DataInputStream(new FileInputStream(fileTest));
        DataInputStream dis = catalogManager.downloadFile(file.getId(), sessionIdUser);
        fis.read(bytesOrig, 0, 100);
        dis.read(bytes, 0, 100);
        fis.close();
        dis.close();
        assertArrayEquals(bytesOrig, bytes);


        int offset = 5;
        int limit = 30;
        dis = catalogManager.downloadFile(file.getId(), offset, limit, sessionIdUser);
        fis = new DataInputStream(new FileInputStream(fileTest));
        for (int i = 0; i < offset; i++) {
            fis.readLine();
        }


        String line;
        int lines = 0;
        while ((line = dis.readLine()) != null) {
            lines++;
            System.out.println(line);
            assertEquals(fis.readLine(), line);
        }

        assertEquals(limit-offset, lines);

        fis.close();
        dis.close();
        fileTest.delete();

    }


    @Test
    public void testDownloadFile() throws CatalogException, IOException, InterruptedException {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");

        String fileName = "item." + TimeUtils.getTimeMillis() + ".vcf";
        int fileSize = 200;
        byte[] bytesOrig = StringUtils.randomString(fileSize).getBytes();
        File file = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "data/" + fileName,
                bytesOrig, "description", true, sessionIdUser).first();

        DataInputStream dis = catalogManager.downloadFile(file.getId(), sessionIdUser);

        byte[] bytes = new byte[fileSize];
        dis.read(bytes, 0, fileSize);
        assertTrue(Arrays.equals(bytesOrig, bytes));

    }

    @Test
    public void renameFileTest() throws CatalogException, IOException {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");
        catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "data/file.txt",
                StringUtils.randomString(200).getBytes(), "description", true, sessionIdUser);
        catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "data/nested/folder/file2.txt",
                StringUtils.randomString(200).getBytes(), "description", true, sessionIdUser);

        catalogManager.renameFile(catalogManager.getFileId("user@1000G:phase1:data/nested/"), "nested2", sessionIdUser);
        Set<String> paths = catalogManager.getAllFiles(studyId, null, sessionIdUser).getResult()
                .stream().map(File::getPath).collect(Collectors.toSet());

        assertTrue(paths.contains("data/nested2/"));
        assertFalse(paths.contains("data/nested/"));
        assertTrue(paths.contains("data/nested2/folder/"));
        assertTrue(paths.contains("data/nested2/folder/file2.txt"));
        assertTrue(paths.contains("data/file.txt"));

        catalogManager.renameFile(catalogManager.getFileId("user@1000G:phase1:data/"), "Data", sessionIdUser);
        paths = catalogManager.getAllFiles(studyId, null, sessionIdUser).getResult()
                .stream().map(File::getPath).collect(Collectors.toSet());

        assertTrue(paths.contains("Data/"));
        assertTrue(paths.contains("Data/file.txt"));
        assertTrue(paths.contains("Data/nested2/"));
        assertTrue(paths.contains("Data/nested2/folder/"));
        assertTrue(paths.contains("Data/nested2/folder/file2.txt"));
    }

    @Test
    public void searchFileTest() throws CatalogException, IOException {

        int studyId = catalogManager.getStudyId("user@1000G:phase1");

        QueryOptions options;
        QueryResult<File> result;

        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.name.toString(), "~^data");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(1, result.getNumResults());

        //Folder "jobs" does not exist
        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.directory.toString(), "jobs");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(0, result.getNumResults());

        //Get all files in data
        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.directory.toString(), "data/");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(1, result.getNumResults());

        //Get all files in data recursively
        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.directory.toString(), "data/.*");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(5, result.getNumResults());

        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.type.toString(), "FILE");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        result.getResult().forEach(f -> assertEquals(File.Type.FILE, f.getType()));
        int numFiles = result.getNumResults();
        assertEquals(3, numFiles);

        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.type.toString(), "FOLDER");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        result.getResult().forEach(f -> assertEquals(File.Type.FOLDER, f.getType()));
        int numFolders = result.getNumResults();
        assertEquals(5, numFolders);


        options = new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.type.toString(), "FILE,FOLDER");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(8, result.getNumResults());
        assertEquals(numFiles + numFolders, result.getNumResults());

        options = new QueryOptions("type", "FILE");
        options.put("diskUsage", ">400");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(2, result.getNumResults());

        options = new QueryOptions("type", "FILE");
        options.put("diskUsage", "<400");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(1, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions("sampleIds", "3,4,5"), sessionIdUser);
        assertEquals(1, result.getNumResults());

        options = new QueryOptions("type", "FILE");
        options.put("format", "PLAIN");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(2, result.getNumResults());

        CatalogFileDBAdaptor.FileFilterOption attributes = CatalogFileDBAdaptor.FileFilterOption.attributes;
        CatalogFileDBAdaptor.FileFilterOption nattributes = CatalogFileDBAdaptor.FileFilterOption.nattributes;

        result = catalogManager.searchFile(studyId, new QueryOptions(attributes + ".field" , "~val"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions("attributes.field" , "~val"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(attributes + ".field", "=~val"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(attributes + ".field", "~val"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(attributes + ".field", "value"), sessionIdUser);
        assertEquals(2, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(attributes + ".field", "other"), sessionIdUser);
        assertEquals(1, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions("nattributes.numValue", ">=5"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions("nattributes.numValue", ">4,<6"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", "==5"), sessionIdUser);
        assertEquals(2, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", "==5.0"), sessionIdUser);
        assertEquals(2, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", "5.0"), sessionIdUser);
        assertEquals(2, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", ">5"), sessionIdUser);
        assertEquals(1, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", ">4"), sessionIdUser);
        assertEquals(3, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", "<6"), sessionIdUser);
        assertEquals(2, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue", "<=5"), sessionIdUser);
        assertEquals(2, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue" , "<5"), sessionIdUser);
        assertEquals(0, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue" , "<2"), sessionIdUser);
        assertEquals(0, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue" , "==23"), sessionIdUser);
        assertEquals(0, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(attributes + ".numValue" , "=~10"), sessionIdUser);
        assertEquals(1, result.getNumResults());

        result = catalogManager.searchFile(studyId, new QueryOptions(nattributes + ".numValue" , "=10"), sessionIdUser);
        assertEquals(0, result.getNumResults());


        QueryOptions query = new QueryOptions();
        query.add(attributes + ".name", "fileTest1k");
        query.add(attributes + ".field", "value");
        result = catalogManager.searchFile(studyId, query, sessionIdUser);
        assertEquals(1, result.getNumResults());

        query = new QueryOptions();
        query.add(attributes + ".name", "fileTest1k");
        query.add(attributes + ".field", "value");
        query.add(attributes + ".numValue", Arrays.asList(8, 9, 10));   //Searching as String. numValue = "10"
        result = catalogManager.searchFile(studyId, query, sessionIdUser);
        assertEquals(1, result.getNumResults());

    }

    @Test
    public void testSearchFileBoolean() throws CatalogException {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");

        QueryOptions options;
        QueryResult<File> result;
        CatalogFileDBAdaptor.FileFilterOption battributes = CatalogFileDBAdaptor.FileFilterOption.battributes;


        options = new QueryOptions(battributes + ".boolean", "true");       //boolean in [true]
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(1, result.getNumResults());

        options = new QueryOptions(battributes + ".boolean", "false");      //boolean in [false]
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(1, result.getNumResults());

        options = new QueryOptions(battributes + ".boolean", "!=false");    //boolean in [null, true]
        options.put("type", "FILE");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(2, result.getNumResults());

        options = new QueryOptions(battributes + ".boolean", "!=true");     //boolean in [null, false]
        options.put("type", "FILE");
        result = catalogManager.searchFile(studyId, options, sessionIdUser);
        assertEquals(2, result.getNumResults());
    }

    @Test
    public void testSearchFileFail1() throws CatalogException {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");
        thrown.expect(CatalogDBException.class);
        catalogManager.searchFile(studyId, new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.nattributes.toString() + ".numValue", "==NotANumber"), sessionIdUser);
    }

    @Test
    public void testSearchFileFail2() throws CatalogException {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");
        thrown.expect(CatalogDBException.class);
        catalogManager.searchFile(studyId, new QueryOptions("badFilter", "badFilter"), sessionIdUser);
    }
    @Test
    public void testSearchFileFail3() throws CatalogException {
        int studyId = catalogManager.getStudyId("user@1000G:phase1");
        thrown.expect(CatalogDBException.class);
        catalogManager.searchFile(studyId, new QueryOptions("id", "~5"), sessionIdUser); //Bad operator
    }

    @Test
    public void testGetFileParent() throws CatalogException, IOException {

        int fileId;
        fileId = catalogManager.getFileId("user@1000G:phase1:data/test/folder/");
        System.out.println(catalogManager.getFile(fileId, null, sessionIdUser));
        QueryResult<File> fileParent = catalogManager.getFileParent(fileId, null, sessionIdUser);
        System.out.println(fileParent);


        fileId = catalogManager.getFileId("user@1000G:phase1:data/");
        System.out.println(catalogManager.getFile(fileId, null, sessionIdUser));
        fileParent = catalogManager.getFileParent(fileId, null, sessionIdUser);
        System.out.println(fileParent);

        fileId = catalogManager.getFileId("user@1000G:phase1:");
        System.out.println(catalogManager.getFile(fileId, null, sessionIdUser));
        fileParent = catalogManager.getFileParent(fileId, null, sessionIdUser);
        System.out.println(fileParent);


    }

    @Test
    public void testGetFileParents1() throws CatalogException {
        int fileId;
        QueryResult<File> fileParents;

        fileId = catalogManager.getFileId("user@1000G:phase1:data/test/folder/");
        fileParents = catalogManager.getFileParents(fileId, null, sessionIdUser);

        assertEquals(4, fileParents.getNumResults());
        assertEquals("", fileParents.getResult().get(0).getPath());
        assertEquals("data/", fileParents.getResult().get(1).getPath());
        assertEquals("data/test/", fileParents.getResult().get(2).getPath());
        assertEquals("data/test/folder/", fileParents.getResult().get(3).getPath());
    }

    @Test
    public void testGetFileParents2() throws CatalogException {
        int fileId;
        QueryResult<File> fileParents;

        fileId = catalogManager.getFileId("user@1000G:phase1:data/test/folder/test_1K.txt.gz");
        fileParents = catalogManager.getFileParents(fileId, null, sessionIdUser);

        assertEquals(4, fileParents.getNumResults());
        assertEquals("", fileParents.getResult().get(0).getPath());
        assertEquals("data/", fileParents.getResult().get(1).getPath());
        assertEquals("data/test/", fileParents.getResult().get(2).getPath());
        assertEquals("data/test/folder/", fileParents.getResult().get(3).getPath());
    }

    @Test
    public void testGetFileParents3() throws CatalogException {
        int fileId;
        QueryResult<File> fileParents;

        fileId = catalogManager.getFileId("user@1000G:phase1:data/test/");
        fileParents = catalogManager.getFileParents(fileId,
                new QueryOptions("include", "projects.studies.files.path,projects.studies.files.id"),
                sessionIdUser);

        assertEquals(3, fileParents.getNumResults());
        assertEquals("", fileParents.getResult().get(0).getPath());
        assertEquals("data/", fileParents.getResult().get(1).getPath());
        assertEquals("data/test/", fileParents.getResult().get(2).getPath());

        fileParents.getResult().forEach(f -> {
            assertNull(f.getName());
            assertNotNull(f.getPath());
            assertTrue(f.getId() != 0);
        });

    }

    @Test
    public void testDeleteFile () throws CatalogException, IOException {

        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();

        List<File> result = catalogManager.getAllFiles(studyId, new QueryOptions("type", "FILE"), sessionIdUser).getResult();
        for (File file : result) {
            catalogManager.deleteFile(file.getId(), sessionIdUser);
        }
        CatalogFileUtils catalogFileUtils = new CatalogFileUtils(catalogManager);
        catalogManager.getAllFiles(studyId, new QueryOptions("type", "FILE"), sessionIdUser).getResult().forEach(f -> {
            assertEquals(f.getStatus(), File.Status.TRASHED);
            assertTrue(f.getName().startsWith(".deleted"));
        });

        int studyId2 = catalogManager.getAllStudies(projectId, null, sessionIdUser).getResult().get(1).getId();
        result = catalogManager.getAllFiles(studyId2, new QueryOptions("type", "FILE"), sessionIdUser).getResult();
        for (File file : result) {
            catalogManager.deleteFile(file.getId(), sessionIdUser);
        }
        catalogManager.getAllFiles(studyId, new QueryOptions("type", "FILE"), sessionIdUser).getResult().forEach(f -> {
            assertEquals(f.getStatus(), File.Status.TRASHED);
            assertTrue(f.getName().startsWith(".deleted"));
        });

    }

    @Test
    public void testDeleteLeafFolder () throws CatalogException, IOException {
        int deletable = catalogManager.getFileId("user@1000G/phase3/data/test/folder/");
        deleteFolderAndCheck(deletable);
    }

    @Test
    public void testDeleteMiddleFolder () throws CatalogException, IOException {
        int deletable = catalogManager.getFileId("user@1000G/phase3/data/");
        deleteFolderAndCheck(deletable);
    }

    @Test
    public void testDeleteRootFolder () throws CatalogException, IOException {
        int deletable = catalogManager.getFileId("user@1000G/phase3/");
        thrown.expect(CatalogException.class);
        deleteFolderAndCheck(deletable);
    }

    @Test
    public void deleteFolderTest() throws CatalogException, IOException {
        List<File> folderFiles = new LinkedList<>();
        int studyId = catalogManager.getStudyId("user@1000G/phase3");
        File folder = catalogManager.createFolder(studyId, Paths.get("folder"), false, null, sessionIdUser).first();
        folderFiles.add(catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "folder/my.txt", StringUtils.randomString(200).getBytes(), "", true, sessionIdUser).first());
        folderFiles.add(catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "folder/my2.txt", StringUtils.randomString(200).getBytes(), "", true, sessionIdUser).first());
        folderFiles.add(catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "folder/my3.txt", StringUtils.randomString(200).getBytes(), "", true, sessionIdUser).first());
        folderFiles.add(catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "folder/subfolder/my4.txt", StringUtils.randomString(200).getBytes(), "", true, sessionIdUser).first());
        folderFiles.add(catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "folder/subfolder/my5.txt", StringUtils.randomString(200).getBytes(), "", true, sessionIdUser).first());
        folderFiles.add(catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.NONE, "folder/subfolder/subsubfolder/my6.txt", StringUtils.randomString(200).getBytes(), "", true, sessionIdUser).first());

        CatalogIOManager ioManager = catalogManager.getCatalogIOManagerFactory().get(catalogManager.getFileUri(folder));
        for (File file : folderFiles) {
            assertTrue(ioManager.exists(catalogManager.getFileUri(file)));
        }

        File stagedFile = catalogManager.createFile(studyId, File.Type.FILE, File.Format.PLAIN, File.Bioformat.NONE, "folder/subfolder/subsubfolder/my_staged.txt",
                null, null, null, File.Status.STAGE, 0, -1, null, -1, null, null, true, null, sessionIdUser).first();

        thrown.expect(CatalogException.class);
        try {
            catalogManager.deleteFolder(folder.getId(), sessionIdUser);
        } finally {
            assertEquals("Folder name should not be modified", folder.getPath(), catalogManager.getFile(folder.getId(), sessionIdUser).first().getPath());
            assertTrue(ioManager.exists(catalogManager.getFileUri(catalogManager.getFile(folder.getId(), sessionIdUser).first())));
            for (File file : folderFiles) {
                assertEquals("File name should not be modified", file.getPath(), catalogManager.getFile(file.getId(), sessionIdUser).first().getPath());
                URI fileUri = catalogManager.getFileUri(catalogManager.getFile(file.getId(), sessionIdUser).first());
                assertTrue("File uri: " + fileUri + " should exist", ioManager.exists(fileUri));
            }
        }
    }

    private void deleteFolderAndCheck(int deletable) throws CatalogException, IOException {
        List<File> allFilesInFolder;
        catalogManager.deleteFolder(deletable, sessionIdUser);

        File file = catalogManager.getFile(deletable, sessionIdUser).first();
        allFilesInFolder = catalogManager.getAllFilesInFolder(deletable, null, sessionIdUser).getResult();
        allFilesInFolder = catalogManager.searchFile(
                catalogManager.getStudyIdByFileId(deletable),
                new QueryOptions("directory", catalogManager.getFile(deletable, sessionIdUser).first().getPath() + ".*"),
                null, sessionIdUser).getResult();

        assertTrue(file.getStatus() == File.Status.TRASHED);
        for (File subFile : allFilesInFolder) {
            assertTrue(subFile.getStatus() == File.Status.TRASHED);
        }
    }

    /* TYPE_FILE UTILS */
    public static java.io.File createDebugFile() throws IOException {
        String fileTestName = "/tmp/fileTest " + StringUtils.randomString(5);
        return createDebugFile(fileTestName);
    }

    public static java.io.File createDebugFile(String fileTestName) throws IOException {
        DataOutputStream os = new DataOutputStream(new FileOutputStream(fileTestName));

        os.writeBytes("Debug file name: " + fileTestName + "\n");
        for (int i = 0; i < 100; i++) {
            os.writeBytes(i + ", ");
        }
        for (int i = 0; i < 200; i++) {
            os.writeBytes(StringUtils.randomString(500));
            os.write('\n');
        }
        os.close();

        return Paths.get(fileTestName).toFile();
    }


    /**
     * Job methods
     * ***************************
     */

    @Test
    public void testCreateJob() throws CatalogException, IOException {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();

        File outDir = catalogManager.createFolder(studyId, Paths.get("jobs", "myJob"), true, null, sessionIdUser).first();

        URI tmpJobOutDir = catalogManager.createJobOutDir(studyId, StringUtils.randomString(5), sessionIdUser);
        catalogManager.createJob(
                studyId, "myJob", "samtool", "description", "echo \"Hello World!\"", tmpJobOutDir, outDir.getId(),
                Collections.emptyList(), new HashMap<>(), null, Job.Status.PREPARED, null, sessionIdUser);

        catalogManager.createJob(
                studyId, "myReadyJob", "samtool", "description", "echo \"Hello World!\"", tmpJobOutDir, outDir.getId(),
                Collections.emptyList(), new HashMap<>(), null, Job.Status.READY, null, sessionIdUser);

        catalogManager.createJob(
                studyId, "myQueuedJob", "samtool", "description", "echo \"Hello World!\"", tmpJobOutDir, outDir.getId(),
                Collections.emptyList(), new HashMap<>(), null, Job.Status.QUEUED, null, sessionIdUser);

        catalogManager.createJob(
                studyId, "myErrorJob", "samtool", "description", "echo \"Hello World!\"", tmpJobOutDir, outDir.getId(),
                Collections.emptyList(), new HashMap<>(), null, Job.Status.ERROR, null, sessionIdUser);

        String sessionId = catalogManager.login("admin", "admin", "localhost").first().get("sessionId").toString();
        QueryResult<Job> unfinishedJobs = catalogManager.getUnfinishedJobs(sessionId);
        assertEquals(2, unfinishedJobs.getNumResults());

        QueryResult<Job> allJobs = catalogManager.getAllJobs(studyId, sessionId);
        assertEquals(4, allJobs.getNumResults());
    }

    @Test
    public void testCreateFailJob() throws CatalogException {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();

        URI tmpJobOutDir = catalogManager.createJobOutDir(studyId, StringUtils.randomString(5), sessionIdUser);
        thrown.expect(CatalogException.class);
        catalogManager.createJob(
                studyId, "myErrorJob", "samtool", "description", "echo \"Hello World!\"", tmpJobOutDir, projectId, //Bad outputId
                Collections.emptyList(), new HashMap<>(), null, Job.Status.ERROR, null, sessionIdUser);
    }

    @Test
    public void testGetAllJobs() throws CatalogException {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();
        File outDir = catalogManager.createFolder(studyId, Paths.get("jobs", "myJob"), true, null, sessionIdUser).first();

        URI tmpJobOutDir = catalogManager.createJobOutDir(studyId, StringUtils.randomString(5), sessionIdUser);
        catalogManager.createJob(
                studyId, "myErrorJob", "samtool", "description", "echo \"Hello World!\"", tmpJobOutDir, outDir.getId(),
                Collections.emptyList(), new HashMap<>(), null, Job.Status.ERROR, null, sessionIdUser);

        QueryResult<Job> allJobs = catalogManager.getAllJobs(studyId, sessionIdUser);

        assertEquals(1, allJobs.getNumTotalResults());
        assertEquals(1, allJobs.getNumResults());
    }

    /**
     * Sample methods
     * ***************************
     */

    @Test
    public void testCreateSample () throws CatalogException {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();

        QueryResult<Sample> sampleQueryResult = catalogManager.createSample(studyId, "HG007", "IMDb", "", null, null, sessionIdUser);
        System.out.println("sampleQueryResult = " + sampleQueryResult);

    }

    @Test
    public void testCreateVariableSet () throws CatalogException {
        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
        int studyId = catalogManager.getAllStudies(projectId, null, sessionIdUser).first().getId();

        Set<Variable> variables = new HashSet<>();
        variables.addAll(Arrays.asList(
                new Variable("NAME", "", Variable.VariableType.TEXT, "", true, Collections.<String>emptyList(), 0, "", "", Collections.<String, Object>emptyMap()),
                new Variable("AGE", "", Variable.VariableType.NUMERIC, null, true, Collections.singletonList("0:99"), 1, "", "", Collections.<String, Object>emptyMap()),
                new Variable("HEIGHT", "", Variable.VariableType.NUMERIC, "1.5", false, Collections.singletonList("0:"), 2, "", "", Collections.<String, Object>emptyMap()),
                new Variable("ALIVE", "", Variable.VariableType.BOOLEAN, "", true, Collections.<String>emptyList(), 3, "", "", Collections.<String, Object>emptyMap()),
                new Variable("PHEN", "", Variable.VariableType.CATEGORICAL, "", true, Arrays.asList("CASE", "CONTROL"), 4, "", "", Collections.<String, Object>emptyMap())
        ));
        QueryResult<VariableSet> sampleQueryResult = catalogManager.createVariableSet(studyId, "vs1", true, "", null, variables, sessionIdUser);
        System.out.println("sampleQueryResult = " + sampleQueryResult);

    }
//
//    @Test
//    public void testAnnotation () throws CatalogException {
//        testCreateSample();
//        testCreateVariableSet();
//        int projectId = catalogManager.getAllProjects("user", null, sessionIdUser).first().getId();
//        Study study = catalogManager.getAllStudies(projectId, null, sessionIdUser).first();
//        int sampleId = catalogManager.getAllSamples(study.getId(), new QueryOptions(), sessionIdUser).first().getId();
//
//        {
//            HashMap<String, Object> annotations = new HashMap<String, Object>();
//            annotations.put("NAME", "Luke");
//            annotations.put("AGE", "28");
//            annotations.put("HEIGHT", "1.78");
//            annotations.put("ALIVE", "1");
//            annotations.put("PHEN", "CASE");
//            QueryResult<AnnotationSet> annotationSetQueryResult = catalogManager.annotateSample(sampleId, "annotation1", study.getVariableSets().get(0).getId(), annotations, null, sessionIdUser);
//            System.out.println("annotationSetQueryResult = " + annotationSetQueryResult);
//        }
//
//        {
//            HashMap<String, Object> annotations = new HashMap<String, Object>();
//            annotations.put("NAME", "Luke");
//            annotations.put("AGE", "95");
////            annotations.put("HEIGHT", "1.78");
//            annotations.put("ALIVE", "1");
//            annotations.put("PHEN", "CASE");
//            QueryResult<AnnotationSet> annotationSetQueryResult = catalogManager.annotateSample(sampleId, "annotation2", study.getVariableSets().get(0).getId(), annotations, null, sessionIdUser);
//            System.out.println("annotationSetQueryResult = " + annotationSetQueryResult);
//        }
//
//    }


    public static void clearCatalog(Properties properties) throws IOException {
        List<DataStoreServerAddress> dataStoreServerAddresses = new LinkedList<>();
        for (String hostPort : properties.getProperty(CatalogManager.CATALOG_DB_HOSTS, "localhost").split(",")) {
            if (hostPort.contains(":")) {
                String[] split = hostPort.split(":");
                Integer port = Integer.valueOf(split[1]);
                dataStoreServerAddresses.add(new DataStoreServerAddress(split[0], port));
            } else {
                dataStoreServerAddresses.add(new DataStoreServerAddress(hostPort, 27017));
            }
        }
        MongoDataStoreManager mongoManager = new MongoDataStoreManager(dataStoreServerAddresses);
        MongoDataStore db = mongoManager.get(properties.getProperty(CatalogManager.CATALOG_DB_DATABASE));
        db.getDb().dropDatabase();

        Path rootdir = Paths.get(URI.create(properties.getProperty(CatalogManager.CATALOG_MAIN_ROOTDIR)));
//        Path rootdir = Paths.get(URI.create(properties.getProperty("CATALOG.MAIN.ROOTDIR")));
        deleteFolderTree(rootdir.toFile());
        Files.createDirectory(rootdir);
    }

    public static void deleteFolderTree(java.io.File folder) {
        java.io.File[] files = folder.listFiles();
        if(files!=null) {
            for(java.io.File f: files) {
                if(f.isDirectory()) {
                    deleteFolderTree(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }
}