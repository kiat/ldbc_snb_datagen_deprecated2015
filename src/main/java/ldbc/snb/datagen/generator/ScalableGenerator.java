/*
 * Copyright (c) 2013 LDBC
 * Linked Data Benchmark Council (http://ldbc.eu)
 *
 * This file is part of ldbc_socialnet_dbgen.
 *
 * ldbc_socialnet_dbgen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ldbc_socialnet_dbgen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ldbc_socialnet_dbgen.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2011 OpenLink Software <bdsmt@openlinksw.com>
 * All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation;  only Version 2 of the License dated
 * June 1991.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package ldbc.snb.datagen.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ldbc.snb.datagen.dictionary.*;
import ldbc.snb.datagen.hadoop.MRWriter;
import ldbc.snb.datagen.hadoop.MapReduceKey;
import ldbc.snb.datagen.objects.*;
import ldbc.snb.datagen.serializer.DataExporter;
import ldbc.snb.datagen.serializer.Statistics;
import ldbc.snb.datagen.util.RandomGeneratorFarm;
import ldbc.snb.datagen.util.ScaleFactor;
import ldbc.snb.datagen.vocabulary.SN;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.Reducer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.*;


public class ScalableGenerator {


    public DatagenParams params;
    public long postId = 0;
    public long groupId = 0;
    private static final int NUM_FRIENDSHIP_HADOOP_JOBS = 3;
    private static final double friendRatioPerJob[]     = {0.45, 0.45, 0.1};
    private static final int USER_RANDOM_ID_LIMIT       = 100;
    private static final int startMonth                 = 1;
    private static final int startDate                  = 1;
    private static final int endMonth                   = 1;
    private static final int endDate                    = 1;
    private static final double alpha                   = 0.4;

    private static final double levelProbs[] = {0.5, 0.8, 1.0};
    /**
     * < @brief Cumulative probability to join a group for the user direct friends, friends of friends and friends of the friends of the user friend.
     */
    private static final double joinProbs[] = {0.7, 0.4, 0.1};
    /**
     * < @brief Probability to join a group for the user direct friends, friends of friends and friends of the friends of the user friend.
     */

    //Files and folders
    private static final String DICTIONARY_DIRECTORY    = "/dictionaries/";
    private static final String IPZONE_DIRECTORY        = "/ipaddrByCountries";
    private static final String STATS_FILE              = "testdata.json";
    private static final String RDF_OUTPUT_FILE         = "ldbc_socialnet_dbg";
    private static final String PARAM_COUNT_FILE        = "factors.txt";

    // Dictionaries dataset files
    private static final String browserDictonryFile         = DICTIONARY_DIRECTORY + "browsersDic.txt";
    private static final String companiesDictionaryFile     = DICTIONARY_DIRECTORY + "companiesByCountry.txt";
    private static final String countryAbbrMappingFile      = DICTIONARY_DIRECTORY + "countryAbbrMapping.txt";
    private static final String popularTagByCountryFile     = DICTIONARY_DIRECTORY + "popularTagByCountry.txt";
    private static final String countryDictionaryFile       = DICTIONARY_DIRECTORY + "dicLocations.txt";
    private static final String tagsFile                    = DICTIONARY_DIRECTORY + "tags.txt";
    private static final String emailDictionaryFile         = DICTIONARY_DIRECTORY + "email.txt";
    private static final String nameDictionaryFile          = DICTIONARY_DIRECTORY + "givennameByCountryBirthPlace.txt.freq.full";
    private static final String universityDictionaryFile    = DICTIONARY_DIRECTORY + "universities.txt";
    private static final String cityDictionaryFile          = DICTIONARY_DIRECTORY + "citiesByCountry.txt";
    private static final String languageDictionaryFile      = DICTIONARY_DIRECTORY + "languagesByCountry.txt";
    private static final String popularDictionaryFile       = DICTIONARY_DIRECTORY + "popularPlacesByCountry.txt";
    private static final String agentFile                   = DICTIONARY_DIRECTORY + "smartPhonesProviders.txt";
    private static final String surnamDictionaryFile        = DICTIONARY_DIRECTORY + "surnameByCountryBirthPlace.txt.freq.sort";
    private static final String tagClassFile                = DICTIONARY_DIRECTORY + "tagClasses.txt";
    private static final String tagClassHierarchyFile       = DICTIONARY_DIRECTORY + "tagClassHierarchy.txt";
    private static final String tagTextFile                 = DICTIONARY_DIRECTORY + "tagText.txt";
    private static final String tagMatrixFile               = DICTIONARY_DIRECTORY + "tagMatrix.txt";
    private static final String flashmobDistFile            = DICTIONARY_DIRECTORY + "flashmobDist.txt";
    private static final String fbSocialDegreeFile          = DICTIONARY_DIRECTORY + "facebookBucket100.dat";


    //final user parameters
    static private final String SCALE_FACTOR        = "scaleFactor";
    static private final String SERIALIZER          = "serializer";
    static private final String EXPORT_TEXT         = "exportText";
    static private final String ENABLE_COMPRESSION  = "enableCompression";
    static private final String NUM_PERSONS         = "numPersons";
    static private final String NUM_YEARS           = "numYears";
    static private final String START_YEAR          = "startYear";

    // Gender string representation, both representations vector/standalone so the string is coherent.
    private final String MALE       = "male";
    private final String FEMALE     = "female";
    private final String gender[]   = {MALE, FEMALE};

    //Stat container
    private Statistics stats;

    // bookkeeping for parameter generation
    private HashMap<Long, ReducedUserProfile.Counts> factorTable;
    private HashMap<Integer, Integer> postsPerCountry;
    private HashMap<Integer, Integer> tagClassCount;
    private HashMap<String, Integer> firstNameCount;
    private HashMap<Integer, Integer> tagNameCount;

    // For sliding window
    static private int windowSize;

    ReducedUserProfile reducedUserProfiles[];

    // For friendship generation
    MRWriter mrWriter;


    // Random values generators
    PowerDistGenerator randomTagPowerLaw;

    static private DateGenerator dateTimeGenerator;
    static private int startYear;
    static private int endYear;

    // Dictionary classes
    static private PlaceDictionary placeDictionary;
    static private LanguageDictionary languageDictionary;
    static private TagDictionary tagDictionary;

    //For facebook-like social degree distribution
    static private FBSocialDegreeGenerator fbDegreeGenerator;
    static private FlashmobTagDictionary flashmobTagDictionary;
    static private TagTextDictionary tagTextDictionary;
    static private TagMatrix tagMatrix;
    static private NamesDictionary namesDictionary;
    static private UniversityDictionary unversityDictionary;
    static private CompanyDictionary companiesDictionary;
    static private UserAgentDictionary userAgentDictionary;
    static private EmailDictionary emailDictionary;
    static private BrowserDictionary browserDictonry;
    static private PopularPlacesDictionary popularDictionary;
    static private IPAddressDictionary ipAddDictionary;
    static private PhotoGenerator photoGenerator;
    static private ForumGenerator forumGenerator;
    static private UniformPostGenerator uniformPostGenerator;
    static private FlashmobPostGenerator flashmobPostGenerator;
    static private CommentGenerator commentGenerator;

    static private long deltaTime = 0;
    static private long dateThreshold = 0;

    static private boolean exportText = true;
    static private boolean enableCompression = true;
    static public final int blockSize = 10000;


    // For serialize to RDF format
    DataExporter dataExporter = null;
    static private String serializerType;
    String outUserProfileName = "userProf.ser";
    String outUserProfile;
    int threadId;
    int numThreads;


    // Data accessed from the hadoop jobs
    private ReducedUserProfile[] cellReducedUserProfiles;
    private int numUserProfilesRead = 0;
    public int totalNumUserProfilesRead = 0;
    private int numUserForNewCell = 0;
    private int mrCurCellPost = 0;
    public static int blockId = 0;
    public int exactOutput = 0;
    private RandomGeneratorFarm randomFarm;
    public int friendshipNum = 0;

    /**
     * Creates the ScalableGenerator
     */
    public ScalableGenerator(int threadId, DatagenParams params) {
        this.params = params;
        this.threadId = threadId;
        init();
        this.stats = new Statistics();
        this.postsPerCountry = new HashMap<Integer, Integer>();
        this.tagClassCount = new HashMap<Integer, Integer>();
        this.firstNameCount = new HashMap<String, Integer>();
        this.tagNameCount = new HashMap<Integer, Integer>();
        if (threadId != -1) {
            outUserProfile = "mr" + threadId + "_" + outUserProfileName;
        }
        this.factorTable = new HashMap<Long, ReducedUserProfile.Counts>();

        this.randomFarm = new RandomGeneratorFarm();
        this.windowSize = params.cellSize * params.numberOfCellPerWindow;                          // We compute the size of the window.
        this.mrWriter = new MRWriter(params.cellSize, windowSize, params.outputDir);
        this.resetWindow();
        this.randomTagPowerLaw = new PowerDistGenerator(params.minNumTagsPerUser, params.maxNumTagsPerUser + 1, alpha);

        // Initializing window memory
        this.reducedUserProfiles = new ReducedUserProfile[windowSize];
        this.cellReducedUserProfiles = new ReducedUserProfile[params.cellSize];
        stats.flashmobTags = flashmobTagDictionary.getFlashmobTags();
    }

    /**
     * Initializes the generator reading the private parameter file, the user parameter file
     * and initialize all the internal variables.
     */
    public void init() {
        loadParamsFromFile();
        dateTimeGenerator = new DateGenerator(new GregorianCalendar(startYear, startMonth, startDate),
                new GregorianCalendar(endYear, endMonth, endDate), alpha, deltaTime);
        if (!params.updateStreams) params.updatePortion = 0.0;
        dateThreshold = dateTimeGenerator.getMaxDateTime() -
                        (long) ((dateTimeGenerator.getMaxDateTime() - dateTimeGenerator.getStartDateTime()) * (params.updatePortion));
        SN.minDate = dateTimeGenerator.getStartDateTime();
        SN.maxDate = dateTimeGenerator.getMaxDateTime();

        System.out.println("Building location dictionary ");
        placeDictionary = new PlaceDictionary(params.numPersons);
        placeDictionary.load(cityDictionaryFile, countryDictionaryFile);

        System.out.println("Building language dictionary ");
        languageDictionary = new LanguageDictionary(placeDictionary,
                                                    params.probEnglish,
                                                    params.probSecondLang);
        languageDictionary.load(languageDictionaryFile);

        System.out.println("Building Tag dictionary ");
        tagDictionary = new TagDictionary(placeDictionary.getCountries().size(),
                                          params.tagCountryCorrProb);
        tagDictionary.load( tagsFile,
                            popularTagByCountryFile,
                            tagClassFile,
                            tagClassHierarchyFile);

        System.out.println("Building Tag-text dictionary ");
        tagTextDictionary = new TagTextDictionary(tagDictionary, params.ratioReduceText);
        tagTextDictionary.load(tagTextFile);

        System.out.println("Building Tag Matrix dictionary ");
        tagMatrix = new TagMatrix(tagDictionary.getNumPopularTags());
        tagMatrix.load(tagMatrixFile);

        System.out.println("Building IP addresses dictionary ");
        ipAddDictionary = new IPAddressDictionary(placeDictionary, params.probDiffIPinTravelSeason, params.probDiffIPnotTravelSeason,
                params.probDiffIPforTraveller);
        ipAddDictionary.load(countryAbbrMappingFile, IPZONE_DIRECTORY);

        System.out.println("Building Names dictionary");
        namesDictionary = new NamesDictionary(
                placeDictionary);
        namesDictionary.load(surnamDictionaryFile, nameDictionaryFile);

        System.out.println("Building email dictionary");
        emailDictionary = new EmailDictionary();
        emailDictionary.load(emailDictionaryFile);

        System.out.println("Building browser dictionary");
        browserDictonry = new BrowserDictionary(params.probAnotherBrowser);
        browserDictonry.load(browserDictonryFile);


        System.out.println("Building companies dictionary");
        companiesDictionary = new CompanyDictionary(placeDictionary, params.probUnCorrelatedCompany);
        companiesDictionary.load(companiesDictionaryFile);

        System.out.println("Building university dictionary");
        unversityDictionary = new UniversityDictionary(placeDictionary,
                params.probUnCorrelatedOrganization, params.probTopUniv, companiesDictionary.getNumCompanies());
        unversityDictionary.load(universityDictionaryFile);

        System.out.println("Building popular places dictionary");
        popularDictionary = new PopularPlacesDictionary(placeDictionary);
        popularDictionary.load(popularDictionaryFile);

        System.out.println("Building user agents dictionary");
        userAgentDictionary = new UserAgentDictionary(params.probSentFromAgent);
        userAgentDictionary.load(agentFile);

        // Building generators.
        System.out.println("Building photo generator");
        photoGenerator = new PhotoGenerator(dateTimeGenerator,
                placeDictionary, 0, popularDictionary, params.probPopularPlaces, params.maxNumLike, deltaTime);

        System.out.println("Building Forum generator");
        forumGenerator = new ForumGenerator(dateTimeGenerator, placeDictionary,
                tagDictionary, params.numPersons);


        /// IMPORTANT: ratioLargeText is divided 0.083333, the probability
        /// that SetUserLargePoster returns true.
        System.out.println("Building Uniform Post Generator");
        uniformPostGenerator = new UniformPostGenerator(dateTimeGenerator,
                tagTextDictionary,
                userAgentDictionary,
                ipAddDictionary,
                browserDictonry,
                params.minTextSize,
                params.maxTextSize,
                params.ratioReduceText,
                params.minLargePostSize,
                params.maxLargePostSize,
                params.ratioLargePost / 0.0833333,
                params.maxNumLike,
                params.exportText,
                params.deltaTime,
                params.maxNumPostPerMonth,
                params.maxNumFriends,
                params.maxNumGroupPostPerMonth,
                params.maxNumMemberGroup
        );
        uniformPostGenerator.initialize();

        System.out.println("Building Flashmob Tag Dictionary");
        flashmobTagDictionary = new FlashmobTagDictionary(tagDictionary,
                dateTimeGenerator,
                params.flashmobTagsPerMonth,
                params.probInterestFlashmobTag,
                params.probRandomPerLevel,
                params.flashmobTagMinLevel,
                params.flashmobTagMaxLevel,
                params.flashmobTagDistExp
        );
        flashmobTagDictionary.initialize();


        /// IMPORTANT: ratioLargeText is divided 0.083333, the probability
        /// that SetUserLargePoster returns true.
        System.out.println("Building Flashmob Post Generator");
        flashmobPostGenerator = new FlashmobPostGenerator(dateTimeGenerator, tagTextDictionary,
                userAgentDictionary,
                ipAddDictionary,
                browserDictonry,
                params.minTextSize,
                params.maxTextSize,
                params.ratioReduceText,
                params.minLargePostSize,
                params.maxLargePostSize,
                params.ratioLargePost / 0.0833333,
                params.maxNumLike,
                params.exportText,
                params.deltaTime,
                flashmobTagDictionary,
                tagMatrix,
                params.maxNumFlashmobPostPerMonth,
                params.maxNumGroupFlashmobPostPerMonth,
                params.maxNumFriends,
                params.maxNumMemberGroup,
                params.maxNumTagPerFlashmobPost,
                flashmobDistFile
        );
        flashmobPostGenerator.initialize();

        /// IMPORTANT: ratioLargeText is divided 0.083333, the probability
        /// that SetUserLargePoster returns true.
        System.out.println("Building Comment Generator");
        commentGenerator = new CommentGenerator(tagDictionary,
                tagTextDictionary,
                tagMatrix,
                dateTimeGenerator,
                browserDictonry,
                ipAddDictionary,
                userAgentDictionary,
                params.minCommentSize,
                params.maxCommentSize,
                params.minLargeCommentSize,
                params.maxLargeCommentSize,
                params.ratioLargeComment / 0.0833333,
                params.maxNumLike, 
                params.exportText,
                params.deltaTime
        );
        commentGenerator.initialize();

        System.out.println("Building Facebook-like social degree generator");
        fbDegreeGenerator = new FBSocialDegreeGenerator(params.numPersons, fbSocialDegreeFile, 0);
    }

    /**
     * Reads and loads the private parameter file and the user parameter file.
     */
    private void loadParamsFromFile() {
        try {
            System.out.println("Loading parameters");
            //First read the internal params.ini

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }


        System.out.println("Finished loading parameters");
    }

    public void openSerializer() {
        dataExporter = getSerializer(serializerType, RDF_OUTPUT_FILE);
    }

    public void closeSerializer() {
        dataExporter.close();
        writeStatistics();
        writeFactorTable();
        System.out.println("Number of generated triples " + dataExporter.unitsGenerated());
        System.out.println("Writing the data for test driver ");
    }

    public void generateUserActivity(ReducedUserProfile userProfile, Reducer<MapReduceKey, ReducedUserProfile, MapReduceKey, ReducedUserProfile>.Context context) {
        int index = numUserProfilesRead % windowSize;
        numUserProfilesRead++;
        reducedUserProfiles[index] = userProfile;
        UserExtraInfo extraInfo = new UserExtraInfo();
        reducedUserProfiles[index].setForumWallId(SN.composeId(groupId, reducedUserProfiles[index].getCreationDate()));
        groupId++;
        setInfoFromUserProfile(reducedUserProfiles[index], extraInfo);
        UserInfo userInfo = new UserInfo();
        userInfo.user = reducedUserProfiles[index];
        userInfo.extraInfo = extraInfo;
        dataExporter.export(userInfo);
        int nameCount = firstNameCount.containsKey(extraInfo.getFirstName()) ? firstNameCount.get(extraInfo.getFirstName()) : 0;
        firstNameCount.put(extraInfo.getFirstName(), nameCount + 1);
        long init = System.currentTimeMillis();
        generatePosts(uniformPostGenerator, reducedUserProfiles[index], extraInfo);
        generatePosts(flashmobPostGenerator, reducedUserProfiles[index], extraInfo);
        generatePhotos(reducedUserProfiles[index], extraInfo);
        generateUserGroups(reducedUserProfiles[index], extraInfo);
        if (numUserProfilesRead % 100 == 0)
            context.setStatus("Generated post and photo for " + numUserProfilesRead + " users");
    }

    private void generateUserGroups(ReducedUserProfile userProfile, UserExtraInfo extraInfo) {
        double moderatorProb = randomFarm.get(RandomGeneratorFarm.Aspect.GROUP_MODERATOR).nextDouble();
        if (moderatorProb <= params.groupModeratorProb) {
            Friend firstLevelFriends[] = userProfile.getFriendList();
            Vector<Friend> secondLevelFriends = new Vector<Friend>();
            //TODO: Include friends of friends a.k.a second level friends?
            int numGroup = randomFarm.get(RandomGeneratorFarm.Aspect.NUM_GROUP).nextInt(params.maxNumGroupCreatedPerUser) + 1;
            for (int j = 0; j < numGroup; j++) {
                createGroupForUser(userProfile, firstLevelFriends, secondLevelFriends);
            }
        }
    }

    public void resetWindow() {
        numUserProfilesRead = 0;
        numUserForNewCell = 0;
        mrCurCellPost = 0;
    }

    public void pushUserProfile(ReducedUserProfile reduceUser, int pass, int outputDimension, Reducer<MapReduceKey, ReducedUserProfile, MapReduceKey, ReducedUserProfile>.Context context) {
        ReducedUserProfile userObject = new ReducedUserProfile();
        userObject.copyFields(reduceUser);
        totalNumUserProfilesRead++;
        if (numUserProfilesRead < windowSize) {                             // Push the user into the window if there is enought space.
            reducedUserProfiles[numUserProfilesRead] = userObject;
            numUserProfilesRead++;
        } else {                                                            // If the window is full, push the user into the backup cell.
            cellReducedUserProfiles[numUserForNewCell] = userObject;
            numUserForNewCell++;
            if (numUserForNewCell == params.cellSize) {                            // Once the backup cell is full, create friendships and slide the window.
                slideCellsFriendShip(pass, mrCurCellPost, params.numberOfCellPerWindow, context, outputDimension);
                int startIndex = (mrCurCellPost % params.numberOfCellPerWindow) * params.cellSize;
                generateCellOfUsers2(startIndex, cellReducedUserProfiles);
                mrCurCellPost++;
                numUserForNewCell = 0;
            }
        }
    }

    public void pushAllRemainingUser(int pass, int outputDimension, Reducer<MapReduceKey, ReducedUserProfile, MapReduceKey, ReducedUserProfile>.Context context) {
        // For each remianing cell in the window, we create the edges.
        for (int numLeftCell = Math.min(params.numberOfCellPerWindow, numUserProfilesRead / params.cellSize); numLeftCell > 0; --numLeftCell, ++mrCurCellPost) {
            slideCellsFriendShip(pass, mrCurCellPost, numLeftCell, context, outputDimension);
        }
        // We write to the context the users that might have been left into not fully filled cell.
        mrWriter.writeReducedUserProfiles(0, numUserForNewCell, outputDimension, cellReducedUserProfiles, context);
        exactOutput += numUserForNewCell;
    }

    private void slideCellsFriendShip(int pass, int cellPos, int numleftCell, Reducer.Context context, int outputDimension) {
        int startIndex = (cellPos % params.numberOfCellPerWindow) * params.cellSize;
        for (int i = 0; i < params.cellSize; i++) {
            int curIdxInWindow = startIndex + i;
            // From this user, check all the user in the window to create friendship
            for (int j = i + 1; (j < (numleftCell * params.cellSize))
                    && reducedUserProfiles[curIdxInWindow].getNumFriends()
                    < reducedUserProfiles[curIdxInWindow].getCorMaxNumFriends(pass);
                 j++) {
                int checkFriendIdx = (curIdxInWindow + j) % windowSize;
                testAndSetFriend(reducedUserProfiles, curIdxInWindow, checkFriendIdx, pass, params.limitProCorrelated);
            }
        }
        updateLastPassFriendAdded(startIndex, startIndex + params.cellSize, pass);
        mrWriter.writeReducedUserProfiles(startIndex, startIndex + params.cellSize, outputDimension, reducedUserProfiles, context);
        exactOutput = exactOutput + params.cellSize;
    }

    boolean testAndSetFriend(ReducedUserProfile[] users, int userAidx, int userBidx, int pass, double baseProb) {
        //     numTries++;
        ReducedUserProfile userA = users[userAidx];
        ReducedUserProfile userB = users[userBidx];
        if (!(userA.getNumFriends() == userA.getCorMaxNumFriends(pass) || userB.getNumFriends() == userB.getCorMaxNumFriends(pass) ||
                userA.isExistFriend(userB.getAccountId()))) {
            double randProb = randomFarm.get(RandomGeneratorFarm.Aspect.UNIFORM).nextDouble();
            double prob = getFriendCreatePro(userAidx, userBidx, pass);
            if ((randProb < prob) || (randProb < baseProb)) {
                createFriendShip(userA, userB, (byte) pass);
                return true;
            }
        }
        return false;
    }

    public void resetState(int seed) {
        blockId = seed;
        postId = 0;
        groupId = 0;
        SN.setMachineNumber(blockId, (int) Math.ceil(params.numPersons / (double) (blockSize)));
        fbDegreeGenerator.resetState(seed);
        resetWindow();
        randomFarm.resetRandomGenerators((long) seed);
        if (dataExporter != null) {
            dataExporter.resetState(seed);
        }
    }


    /**
     * Generates the users. The user generation process is divided in blocks of size four times the
     * size of the window. At the beginning of each block, the seeds of the random number generators
     * are reset. This is for the sake of determinism, so independently of the mapper that receives a
     * block, the seeds are set deterministically, and therefore, we make this generation phase
     * deterministic. This implies that the different mappers have to process blocks of full size,
     * that is, a block have to be fully processed by a mapper.
     *
     * @param pass    The pass identifying the current pass.
     * @param context The map-reduce context.
     */
    public void mrGenerateUserInfo(int pass, Context context) {

        if (params.numPersons % params.cellSize != 0) {
            System.err.println("Number of users should be a multiple of the cellsize");
            System.exit(-1);
        }

        // Here we determine the blocks in the "block space" that this mapper is responsible for.
        int numBlocks = (int) (Math.ceil(params.numPersons / (double) blockSize));
        int initBlock = (int) (Math.ceil((numBlocks / (double) numThreads) * threadId));
        int endBlock = (int) (Math.ceil((numBlocks / (double) numThreads) * (threadId + 1)));

        int numUsersToGenerate = 0;
        for (int i = initBlock; i < endBlock; ++i) {
            // Setting the state for the block
            resetState(i);
            for (int j = i * blockSize; j < (i + 1) * blockSize && j < params.numPersons; ++j) {
                ReducedUserProfile reduceUserProf = generateGeneralInformation(j);
                ++numUsersToGenerate;
                try {
                    int block = 0;                                                                  // The mapreduce group this university will be assigned.
                    int key = reduceUserProf.getCorId(pass);                                  // The key used to sort within the block.
                    long id = reduceUserProf.getAccountId();                                         // The id used to sort within the key, to guarantee determinism.
                    MapReduceKey mpk = new MapReduceKey(block, key, id);
                    context.write(mpk, reduceUserProf);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Number of generated users: " + numUsersToGenerate);
    }

    private void generateCellOfUsers2(int newStartIndex, ReducedUserProfile[] _cellReduceUserProfiles) {
        int curIdxInWindow;
        for (int i = 0; i < params.numPersons; i++) {
            curIdxInWindow = newStartIndex + i;
            reducedUserProfiles[curIdxInWindow] = _cellReduceUserProfiles[i];
        }
    }


    private void generatePosts(PostGenerator postGenerator, ReducedUserProfile user, UserExtraInfo extraInfo) {
        Vector<Post> createdPosts = postGenerator.createPosts(randomFarm, user, extraInfo, postId);
        postId += createdPosts.size();
        Iterator<Post> it = createdPosts.iterator();

        while (it.hasNext()) {
            Post post = it.next();
            if (post.getTags() != null) {
                for (Integer t : post.getTags()) {
                    Integer tagClass = tagDictionary.getTagClass(t);
                    Integer tagCount = tagClassCount.containsKey(tagClass) ? tagClassCount.get(tagClass) : 0;
                    tagClassCount.put(tagClass, tagCount + 1);
                    tagCount = tagNameCount.containsKey(t) ? tagNameCount.get(t) : 0;
                    tagNameCount.put(t, tagCount + 1);
                }
            }

            int locationID = ipAddDictionary.getLocation(post.getIpAddress());
            String countryName = placeDictionary.getPlaceName(locationID);
            stats.countries.add(countryName);

            int postCount = postsPerCountry.containsKey(locationID) ? postsPerCountry.get(locationID) : 0;
            postsPerCountry.put(locationID, postCount + 1);

            GregorianCalendar date = new GregorianCalendar();
            date.setTimeInMillis(post.getCreationDate());
            String strCreationDate = DateGenerator.formatYear(date);

            if (stats.maxPostCreationDate == null) {
                stats.maxPostCreationDate = strCreationDate;
                stats.minPostCreationDate = strCreationDate;
            } else {
                if (stats.maxPostCreationDate.compareTo(strCreationDate) < 0) {
                    stats.maxPostCreationDate = strCreationDate;
                }
                if (stats.minPostCreationDate.compareTo(strCreationDate) > 0) {
                    stats.minPostCreationDate = strCreationDate;
                }
            }
            dataExporter.export(post);
            // Generate comments
            int numComment = randomFarm.get(RandomGeneratorFarm.Aspect.NUM_COMMENT).nextInt(params.maxNumComments + 1);
            ArrayList<Message> replyCandidates = new ArrayList<Message>();
            replyCandidates.add(post);
            for (int l = 0; l < numComment; l++) {
                int replyIndex = randomFarm.get(RandomGeneratorFarm.Aspect.REPLY_TO).nextInt(replyCandidates.size());
                Message replyTo = replyCandidates.get(replyIndex);
                Comment comment = commentGenerator.createComment(randomFarm, postId, post, replyTo, user);

                if (comment != null) {
                    if (!factorTable.containsKey(replyTo.getAuthorId())) {
                        factorTable.put(replyTo.getAuthorId(), new ReducedUserProfile.Counts());
                    }
                    factorTable.get(replyTo.getAuthorId()).numberOfPostReplies++;

                    if (comment.getTags() != null) {
                        for (Integer t : comment.getTags()) {
                            Integer tagClass = tagDictionary.getTagClass(t);
                            Integer tagCount = tagClassCount.containsKey(tagClass) ? tagClassCount.get(tagClass) : 0;
                            tagClassCount.put(tagClass, tagCount + 1);
                            tagCount = tagNameCount.containsKey(t) ? tagNameCount.get(t) : 0;
                            tagNameCount.put(t, tagCount + 1);
                        }
                    }
                    locationID = ipAddDictionary.getLocation(comment.getIpAddress());
                    countryName = placeDictionary.getPlaceName(locationID);
                    postCount = postsPerCountry.containsKey(locationID) ? postsPerCountry.get(locationID) : 0;
                    postsPerCountry.put(locationID, postCount + 1);
                    stats.countries.add(countryName);
                    dataExporter.export(comment);
                    if (comment.getTextSize() > 10) replyCandidates.add(comment);
                    postId++;
                }
            }
        }
    }

    private void generatePhotos(ReducedUserProfile user, UserExtraInfo extraInfo) {
        // Generate photo Album and photos
        int numOfmonths = (int) dateTimeGenerator.numberOfMonths(user);
        int numPhotoAlbums = randomFarm.get(RandomGeneratorFarm.Aspect.NUM_PHOTO_ALBUM).nextInt(params.maxNumPhotoAlbumsPerMonth + 1);
        if (numOfmonths != 0) {
            numPhotoAlbums = numOfmonths * numPhotoAlbums;
        }

        for (int m = 0; m < numPhotoAlbums; m++) {
            Forum album = forumGenerator.createAlbum(randomFarm, groupId, user, extraInfo, m, joinProbs[0]);
            if (album != null) {
                groupId++;
                dataExporter.export(album);

                // Generate photos for this album
                int numPhotos = randomFarm.get(RandomGeneratorFarm.Aspect.NUM_PHOTO).nextInt(params.maxNumPhotoPerAlbums + 1);

                for (int l = 0; l < numPhotos; l++) {
                    Photo photo = photoGenerator.generatePhoto(user, album, l, postId, randomFarm);
                    if (photo != null) {
                        postId++;
                        photo.setUserAgent(userAgentDictionary.getUserAgentName(randomFarm.get(RandomGeneratorFarm.Aspect.USER_AGENT_SENT), user.isHaveSmartPhone(), user.getAgentId()));
                        photo.setBrowserIdx(browserDictonry.getPostBrowserId(randomFarm.get(RandomGeneratorFarm.Aspect.DIFF_BROWSER), randomFarm.get(RandomGeneratorFarm.Aspect.BROWSER), user.getBrowserId()));
                        photo.setIpAddress(ipAddDictionary.getIP(randomFarm.get(RandomGeneratorFarm.Aspect.IP), randomFarm.get(RandomGeneratorFarm.Aspect.DIFF_IP), randomFarm.get(RandomGeneratorFarm.Aspect.DIFF_IP_FOR_TRAVELER), user.getIpAddress(),
                                user.isFrequentChange(), photo.getCreationDate(), photo.getLocationId()));
                        int locationID = ipAddDictionary.getLocation(photo.getIpAddress());
                        String countryName = placeDictionary.getPlaceName(locationID);
                        int postCount = postsPerCountry.containsKey(locationID) ? postsPerCountry.get(locationID) : 0;
                        postsPerCountry.put(locationID, postCount + 1);
                        stats.countries.add(countryName);
                        dataExporter.export(photo);

                        if (photo.getTags() != null) {
                            for (Integer t : photo.getTags()) {
                                Integer tagClass = tagDictionary.getTagClass(t);
                                Integer tagCount = tagClassCount.containsKey(tagClass) ? tagClassCount.get(tagClass) : 0;
                                tagClassCount.put(tagClass, tagCount + 1);
                                tagCount = tagNameCount.containsKey(t) ? tagNameCount.get(t) : 0;
                                tagNameCount.put(t, tagCount + 1);
                            }
                        }
                    }
                }
            }
        }
    }

    private void createGroupForUser(ReducedUserProfile user,
                                    Friend firstLevelFriends[], Vector<Friend> secondLevelFriends) {
        double randLevelProb;
        double randMemberProb;

        Forum forum = forumGenerator.createForum(randomFarm, groupId, user);

        if (forum != null) {
            groupId++;
            TreeSet<Long> memberIds = new TreeSet<Long>();

            int numGroupMember = randomFarm.get(RandomGeneratorFarm.Aspect.NUM_USERS_PER_GROUP).nextInt(params.maxNumMemberGroup);
            forum.initAllMemberships(numGroupMember);

            int numLoop = 0;
            while ((forum.getNumMemberAdded() < numGroupMember) && (numLoop < windowSize)) {
                randLevelProb = randomFarm.get(RandomGeneratorFarm.Aspect.FRIEND_LEVEL).nextDouble();
                // Select the appropriate friend level
                if (randLevelProb < levelProbs[0] && user.getNumFriends() > 0) { // ==> level 1
                    int friendIdx = randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP_INDEX).nextInt(user.getNumFriends());
                    long potentialMemberAcc = firstLevelFriends[friendIdx].getFriendAcc();
                    randMemberProb = randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP).nextDouble();
                    if (randMemberProb < joinProbs[0]) {
                        // Check whether this user has been added and then add to the forum
                        if (!memberIds.contains(potentialMemberAcc)) {
                            memberIds.add(potentialMemberAcc);
                            // Assume the earliest membership date is the friendship created date
                            ForumMembership memberShip = forumGenerator.createForumMember(randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP_INDEX),
                                    potentialMemberAcc, forum.getCreatedDate(),
                                    firstLevelFriends[friendIdx]);
                            if (memberShip != null) {
                                memberShip.setForumId(forum.getForumId());
                                forum.addMember(memberShip);
                            }
                        }
                    }
                } else if (randLevelProb < levelProbs[1]) { // ==> level 2
                    if (secondLevelFriends.size() != 0) {
                        int friendIdx = randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP_INDEX).nextInt(secondLevelFriends.size());
                        long potentialMemberAcc = secondLevelFriends.get(friendIdx).getFriendAcc();
                        randMemberProb = randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP).nextDouble();
                        if (randMemberProb < joinProbs[1]) {
                            // Check whether this user has been added and then add to the forum
                            if (!memberIds.contains(potentialMemberAcc)) {
                                memberIds.add(potentialMemberAcc);
                                // Assume the earliest membership date is the friendship created date
                                ForumMembership memberShip = forumGenerator.createForumMember(randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP_INDEX),
                                        potentialMemberAcc, forum.getCreatedDate(),
                                        secondLevelFriends.get(friendIdx));
                                if (memberShip != null) {
                                    memberShip.setForumId(forum.getForumId());
                                    forum.addMember(memberShip);
                                }
                            }
                        }
                    }
                } else { // ==> random users
                    // Select a user from window
                    int friendIdx = randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP_INDEX).nextInt(Math.min(numUserProfilesRead, windowSize));
                    long potentialMemberAcc = reducedUserProfiles[friendIdx].getAccountId();
                    randMemberProb = randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP).nextDouble();
                    if (randMemberProb < joinProbs[2]) {
                        // Check whether this user has been added and then add to the forum
                        if (!memberIds.contains(potentialMemberAcc)) {
                            memberIds.add(potentialMemberAcc);
                            ForumMembership memberShip = forumGenerator.createForumMember(randomFarm.get(RandomGeneratorFarm.Aspect.MEMBERSHIP_INDEX),
                                    potentialMemberAcc, forum.getCreatedDate(),
                                    reducedUserProfiles[friendIdx]);
                            if (memberShip != null) {
                                memberShip.setForumId(forum.getForumId());
                                forum.addMember(memberShip);
                            }
                        }
                    }
                }
                numLoop++;
            }

            dataExporter.export(forum);
            generatePostForGroup(uniformPostGenerator, forum);
            generatePostForGroup(flashmobPostGenerator, forum);
        }
    }

    private void generatePostForGroup(PostGenerator postGenerator, Forum forum) {
        Vector<Post> createdPosts = postGenerator.createPosts(randomFarm, forum, postId);
        postId += createdPosts.size();
        Iterator<Post> it = createdPosts.iterator();

        while (it.hasNext()) {
            Post groupPost = it.next();
            if (groupPost.getTags() != null) {
                for (Integer t : groupPost.getTags()) {
                    Integer tagClass = tagDictionary.getTagClass(t);
                    Integer tagCount = tagClassCount.containsKey(tagClass) ? tagClassCount.get(tagClass) : 0;
                    tagClassCount.put(tagClass, tagCount + 1);
                    tagCount = tagNameCount.containsKey(t) ? tagNameCount.get(t) : 0;
                    tagNameCount.put(t, tagCount + 1);
                }
            }

            int locationID = ipAddDictionary.getLocation(groupPost.getIpAddress());
            String countryName = placeDictionary.getPlaceName(locationID);
            int postCount = postsPerCountry.containsKey(locationID) ? postsPerCountry.get(locationID) : 0;
            postsPerCountry.put(locationID, postCount + 1);
            stats.countries.add(countryName);
            dataExporter.export(groupPost);

            int numComment = randomFarm.get(RandomGeneratorFarm.Aspect.NUM_COMMENT).nextInt(params.maxNumComments + 1);
            ArrayList<Message> replyCandidates = new ArrayList<Message>();
            replyCandidates.add(groupPost);
            for (int j = 0; j < numComment; j++) {
                int replyIndex = randomFarm.get(RandomGeneratorFarm.Aspect.REPLY_TO).nextInt(replyCandidates.size());
                Message replyTo = replyCandidates.get(replyIndex);
                Comment comment = commentGenerator.createComment(randomFarm, postId, groupPost, replyTo, forum);
                if (comment != null) {
                    if (!factorTable.containsKey(replyTo.getAuthorId())) {
                        factorTable.put(replyTo.getAuthorId(), new ReducedUserProfile.Counts());
                    }
                    factorTable.get(replyTo.getAuthorId()).numberOfPostReplies++;

                    locationID = ipAddDictionary.getLocation(comment.getIpAddress());
                    countryName = placeDictionary.getPlaceName(locationID);
                    postCount = postsPerCountry.containsKey(locationID) ? postsPerCountry.get(locationID) : 0;
                    postsPerCountry.put(locationID, postCount + 1);
                    stats.countries.add(countryName);
                    dataExporter.export(comment);
                    if (comment.getTextSize() > 10) replyCandidates.add(comment);

                    if (comment.getTags() != null) {
                        for (Integer t : comment.getTags()) {
                            Integer tagClass = tagDictionary.getTagClass(t);
                            Integer tagCount = tagClassCount.containsKey(tagClass) ? tagClassCount.get(tagClass) : 0;
                            tagClassCount.put(tagClass, tagCount + 1);
                            tagCount = tagNameCount.containsKey(t) ? tagNameCount.get(t) : 0;
                            tagNameCount.put(t, tagCount + 1);
                        }
                    }

                    postId++;
                }
            }
        }
    }

    private long composeUserId(long id, long date, long spid) {
        long spidMask = ~(0xFFFFFFFFFFFFFFFFL << 7);
        long idMask = ~(0xFFFFFFFFFFFFFFFFL << 33);
        long bucket = (long) (256 * (date - SN.minDate) / (double) SN.maxDate);
        return (bucket << 40) | ((id & idMask) << 7) | (spid & spidMask);
    }

    private ReducedUserProfile generateGeneralInformation(int accountId) {
        // User Creation
        long creationDate = dateTimeGenerator.randomDateInMillis(randomFarm.get(RandomGeneratorFarm.Aspect.DATE));
        int countryId = placeDictionary.getCountryForUser(randomFarm.get(RandomGeneratorFarm.Aspect.COUNTRY));
        ReducedUserProfile userProf = new ReducedUserProfile();
        userProf.setCreationDate(creationDate);
        userProf.setGender((randomFarm.get(RandomGeneratorFarm.Aspect.GENDER).nextDouble() > 0.5) ? (byte) 1 : (byte) 0);
        userProf.setBirthDay(dateTimeGenerator.getBirthDay(randomFarm.get(RandomGeneratorFarm.Aspect.BIRTH_DAY), creationDate));
        userProf.setBrowserId(browserDictonry.getRandomBrowserId(randomFarm.get(RandomGeneratorFarm.Aspect.BROWSER)));
        userProf.setCountryId(countryId);
        userProf.setCityId(placeDictionary.getRandomCity(randomFarm.get(RandomGeneratorFarm.Aspect.CITY), countryId));
        userProf.setIpAddress(ipAddDictionary.getRandomIPFromLocation(randomFarm.get(RandomGeneratorFarm.Aspect.IP), countryId));
        userProf.setMaxNumFriends(fbDegreeGenerator.getSocialDegree());
        userProf.setNumCorDimensions(NUM_FRIENDSHIP_HADOOP_JOBS);
        userProf.setAccountId(composeUserId(accountId, creationDate, fbDegreeGenerator.getIDByPercentile()));

        // Setting the number of friends and friends per pass
        short totalFriendSet = 0;
        for (int i = 0; i < NUM_FRIENDSHIP_HADOOP_JOBS - 1; i++) {
            short numPassFriend = (short) Math.floor(friendRatioPerJob[i] * userProf.getMaxNumFriends());
            totalFriendSet = (short) (totalFriendSet + numPassFriend);
            userProf.setCorMaxNumFriends(i, totalFriendSet);
        }
        userProf.setCorMaxNumFriends(NUM_FRIENDSHIP_HADOOP_JOBS - 1, userProf.getMaxNumFriends());
        // Setting tags
        int userMainTag = tagDictionary.getaTagByCountry(randomFarm.get(RandomGeneratorFarm.Aspect.TAG_OTHER_COUNTRY), randomFarm.get(RandomGeneratorFarm.Aspect.TAG), userProf.getCountryId());
        userProf.setMainTag(userMainTag);
        short numTags = ((short) randomTagPowerLaw.getValue(randomFarm.get(RandomGeneratorFarm.Aspect.NUM_TAG)));
        userProf.setInterests(tagMatrix.getSetofTags(randomFarm.get(RandomGeneratorFarm.Aspect.TOPIC), randomFarm.get(RandomGeneratorFarm.Aspect.TAG_OTHER_COUNTRY), userMainTag, numTags));


        userProf.setUniversityLocationId(unversityDictionary.getRandomUniversity(randomFarm, userProf.getCountryId()));

        // Set whether the user has a smartphone or not.
        userProf.setHaveSmartPhone(randomFarm.get(RandomGeneratorFarm.Aspect.USER_AGENT).nextDouble() > params.probHavingSmartPhone);
        if (userProf.isHaveSmartPhone()) {
            userProf.setAgentId(userAgentDictionary.getRandomUserAgentIdx(randomFarm.get(RandomGeneratorFarm.Aspect.USER_AGENT)));
        }

        // Compute the popular places the user uses to visit.
        byte numPopularPlaces = (byte) randomFarm.get(RandomGeneratorFarm.Aspect.NUM_POPULAR).nextInt(params.maxNumPopularPlaces + 1);
        Vector<Short> auxPopularPlaces = new Vector<Short>();
        for (int i = 0; i < numPopularPlaces; i++) {
            short aux = popularDictionary.getPopularPlace(randomFarm.get(RandomGeneratorFarm.Aspect.POPULAR), userProf.getCountryId());
            if (aux != -1) {
                auxPopularPlaces.add(aux);
            }
        }

        // Setting popular places
        short popularPlaces[] = new short[auxPopularPlaces.size()];
        Iterator<Short> it = auxPopularPlaces.iterator();
        int i = 0;
        while (it.hasNext()) {
            popularPlaces[i] = it.next();
            ++i;
        }
        userProf.setPopularPlaceIds(popularPlaces);

        // Set random Index used to sort users randomly
        userProf.setRandomId(randomFarm.get(RandomGeneratorFarm.Aspect.RANDOM).nextInt(USER_RANDOM_ID_LIMIT));

        // Set whether the user is a large poster or not.
        userProf.setLargePoster(IsUserALargePoster(userProf));
        return userProf;
    }

    private boolean IsUserALargePoster(ReducedUserProfile user) {
        if (dateTimeGenerator.getBirthMonth(user.getBirthDay()) == GregorianCalendar.JANUARY) {
            return true;
        }
        return false;
    }


    private void setInfoFromUserProfile(ReducedUserProfile user,
                                        UserExtraInfo userExtraInfo) {

        int locationId = (user.getCityId() != -1) ? user.getCityId() : user.getCountryId();
        userExtraInfo.setLocationId(locationId);
        userExtraInfo.setLocation(placeDictionary.getPlaceName(locationId));
        double distance = randomFarm.get(RandomGeneratorFarm.Aspect.EXACT_LONG_LAT).nextDouble() * 2;
        userExtraInfo.setLatt(placeDictionary.getLatt(user.getCountryId()) + distance);
        userExtraInfo.setLongt(placeDictionary.getLongt(user.getCountryId()) + distance);
        userExtraInfo.setUniversity(unversityDictionary.getUniversityFromLocation(user.getUniversityLocationId()));

        boolean isMale;
        if (user.getGender() == 1) {
            isMale = true;
            userExtraInfo.setGender(gender[0]); // male
        } else {
            isMale = false;
            userExtraInfo.setGender(gender[1]); // female
        }

        userExtraInfo.setFirstName(namesDictionary.getRandomName(randomFarm.get(RandomGeneratorFarm.Aspect.NAME),
                user.getCountryId(), isMale, dateTimeGenerator.getBirthYear(user.getBirthDay())));
        userExtraInfo.setLastName(namesDictionary.getRandomSurname(randomFarm.get(RandomGeneratorFarm.Aspect.SURNAME), user.getCountryId()));

        // email is created by using the user's first name + userId
        int numEmails = randomFarm.get(RandomGeneratorFarm.Aspect.EXTRA_INFO).nextInt(params.maxEmails) + 1;
        double prob = randomFarm.get(RandomGeneratorFarm.Aspect.EXTRA_INFO).nextDouble();
        if (prob >= params.missingRatio) {
            String base = userExtraInfo.getFirstName();
            base = Normalizer.normalize(base, Normalizer.Form.NFD);
            base = base.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            base = base.replaceAll(" ", ".");
            base = base.replaceAll("[.]+", ".");
            for (int i = 0; i < numEmails; i++) {
                String email = base + "" + user.getAccountId() + "@" + emailDictionary.getRandomEmail(randomFarm.get(RandomGeneratorFarm.Aspect.TOP_EMAIL), randomFarm.get(RandomGeneratorFarm.Aspect.EMAIL));
                userExtraInfo.addEmail(email);
            }
        }

        // Set class year
        prob = randomFarm.get(RandomGeneratorFarm.Aspect.EXTRA_INFO).nextDouble();
        if ((prob < params.missingRatio) || userExtraInfo.getUniversity() == -1) {
            userExtraInfo.setClassYear(-1);
        } else {
            userExtraInfo.setClassYear(dateTimeGenerator.getClassYear(randomFarm.get(RandomGeneratorFarm.Aspect.DATE),
                    user.getCreationDate(), user.getBirthDay()));
        }

        // Set company and workFrom
        int numCompanies = randomFarm.get(RandomGeneratorFarm.Aspect.EXTRA_INFO).nextInt(params.maxCompanies) + 1;
        prob = randomFarm.get(RandomGeneratorFarm.Aspect.EXTRA_INFO).nextDouble();
        if (prob >= params.missingRatio) {
            for (int i = 0; i < numCompanies; i++) {
                long workFrom;
                if (userExtraInfo.getClassYear() != -1) {
                    workFrom = dateTimeGenerator.getWorkFromYear(randomFarm.get(RandomGeneratorFarm.Aspect.DATE),
                            user.getCreationDate(),
                            user.getBirthDay());
                } else {
                    workFrom = dateTimeGenerator.getWorkFromYear(randomFarm.get(RandomGeneratorFarm.Aspect.DATE), userExtraInfo.getClassYear());
                }
                long company = companiesDictionary.getRandomCompany(randomFarm, user.getCountryId());
                userExtraInfo.addCompany(company, workFrom);
                user.addNumOfWorkPlaces(1);
                String countryName = placeDictionary.getPlaceName(companiesDictionary.getCountry(company));
                stats.countries.add(countryName);

                GregorianCalendar date = new GregorianCalendar();
                date.setTimeInMillis(workFrom);
                String strWorkFrom = DateGenerator.formatYear(date);
                if (stats.maxWorkFrom == null) {
                    stats.maxWorkFrom = strWorkFrom;
                    stats.minWorkFrom = strWorkFrom;
                } else {
                    if (stats.maxWorkFrom.compareTo(strWorkFrom) < 0) {
                        stats.maxWorkFrom = strWorkFrom;
                    }
                    if (stats.minWorkFrom.compareTo(strWorkFrom) > 0) {
                        stats.minWorkFrom = strWorkFrom;
                    }
                }
            }
        }
        ArrayList<Integer> userLanguages = languageDictionary.getLanguages(randomFarm.get(RandomGeneratorFarm.Aspect.LANGUAGE), user.getCountryId());
        int internationalLang = languageDictionary.getInternationlLanguage(randomFarm.get(RandomGeneratorFarm.Aspect.LANGUAGE));
        if (internationalLang != -1 && userLanguages.indexOf(internationalLang) == -1) {
            userLanguages.add(internationalLang);
        }
        userExtraInfo.setLanguages(userLanguages);

        stats.maxPersonId = Math.max(stats.maxPersonId, user.getAccountId());
        stats.minPersonId = Math.min(stats.minPersonId, user.getAccountId());
        stats.firstNames.add(userExtraInfo.getFirstName());
        String countryName = placeDictionary.getPlaceName(user.getCountryId());
        stats.countries.add(countryName);

        TreeSet<Integer> tags = user.getInterests();
        for (Integer tagID : tags) {
            stats.tagNames.add(tagDictionary.getName(tagID));
            Integer parent = tagDictionary.getTagClass(tagID);
            while (parent != -1) {
                stats.tagClasses.add(tagDictionary.getClassName(parent));
                parent = tagDictionary.getClassParent(parent);
            }
        }
    }


    public double getFriendCreatePro(int i, int j, int pass) {
        double prob;
        if (j > i) {
            prob = Math.pow(params.baseProbCorrelated, (j - i));
        } else {
            prob = Math.pow(params.baseProbCorrelated, (j + windowSize - i));
        }
        return prob;
    }

    private void createFriendShip(ReducedUserProfile user1, ReducedUserProfile user2, byte pass) {

        long requestedTime = dateTimeGenerator.randomFriendRequestedDate(randomFarm.get(RandomGeneratorFarm.Aspect.DATE), user1, user2);
        byte initiator = (byte) randomFarm.get(RandomGeneratorFarm.Aspect.INITIATOR).nextInt(2);
        long createdTime = -1;
        long declinedTime = -1;
        if (randomFarm.get(RandomGeneratorFarm.Aspect.FRIEND_REJECT).nextDouble() > params.friendRejectRatio) {
            createdTime = dateTimeGenerator.randomFriendApprovedDate(randomFarm.get(RandomGeneratorFarm.Aspect.DATE), requestedTime);
        } else {
            declinedTime = dateTimeGenerator.randomFriendDeclinedDate(randomFarm.get(RandomGeneratorFarm.Aspect.DATE), requestedTime);
            if (randomFarm.get(RandomGeneratorFarm.Aspect.FRIEND_APROVAL).nextDouble() < params.friendReApproveRatio) {
                createdTime = dateTimeGenerator.randomFriendReapprovedDate(randomFarm.get(RandomGeneratorFarm.Aspect.DATE), declinedTime);
            }
        }
        createdTime = createdTime - user1.getCreationDate() >= deltaTime ? createdTime : createdTime + (deltaTime - (createdTime - user1.getCreationDate()));
        createdTime = createdTime - user2.getCreationDate() >= deltaTime ? createdTime : createdTime + (deltaTime - (createdTime - user2.getCreationDate()));
        if (createdTime <= dateTimeGenerator.getEndDateTime()) {
            user2.addNewFriend(new Friend(user2, user1, requestedTime, declinedTime,
                    createdTime, pass, initiator));
            user1.addNewFriend(new Friend(user1, user2, requestedTime, declinedTime,
                    createdTime, pass, initiator));
            friendshipNum++;
        }
    }


    private void updateLastPassFriendAdded(int from, int to, int pass) {
        if (to > windowSize) {
            for (int i = from; i < windowSize; i++) {
                reducedUserProfiles[i].setCorNumFriends(pass, reducedUserProfiles[i].getNumFriends());
            }
            for (int i = 0; i < to - windowSize; i++) {
                reducedUserProfiles[i].setCorNumFriends(pass, reducedUserProfiles[i].getNumFriends());
            }
        } else {
            for (int i = from; i < to; i++) {
                reducedUserProfiles[i].setCorNumFriends(pass, reducedUserProfiles[i].getNumFriends());
            }
        }
    }

    private DataExporter getSerializer(String type, String outputFileName) {
        String t = type.toLowerCase();
        DataExporter.DataFormat format;
        String configFile = new String("");
        if (t.equals("ttl")) {
            format = DataExporter.DataFormat.TURTLE;
        } else if (t.equals("n3")) {
            format = DataExporter.DataFormat.N3;
        } else if (t.equals("csv")) {
            format = DataExporter.DataFormat.CSV;
            //           configFile = new String(CSV_DIRECTORY+"/csv.xml");
        } else if (t.equals("csv_merge_foreign")) {
            format = DataExporter.DataFormat.CSV_MERGE_FOREIGN;
//            configFile = new String(CSV_DIRECTORY+"/csvMergeForeign.xml");
        } else if (t.equals("none")) {
            format = DataExporter.DataFormat.NONE;
        } else {
            System.err.println("Unexpected Serializer - Aborting");
            System.exit(-1);
            return null;
        }
        return new DataExporter(format, params.outputDir, threadId, dateThreshold,
                exportText, enableCompression, 1, tagDictionary, browserDictonry, companiesDictionary,
                unversityDictionary, ipAddDictionary, placeDictionary, languageDictionary, configFile, factorTable, startMonth, startYear, stats);
    }

    private void writeFactorTable( ) {
        Configuration conf = new Configuration();
        try {
            FileSystem fs = FileSystem.get(conf);
            OutputStream writer = fs.create(new Path(params.outputDir + "/" + "m" + threadId + PARAM_COUNT_FILE));
            writer.write(Integer.toString(factorTable.size()).getBytes());
            writer.write("\n".getBytes());

            for (Map.Entry<Long, ReducedUserProfile.Counts> c : factorTable.entrySet()) {
                ReducedUserProfile.Counts count = c.getValue();
                // correct the group counts
                //count.numberOfGroups += count.numberOfFriends;
                StringBuffer strbuf = new StringBuffer();
                strbuf.append(c.getKey());
                strbuf.append(",");
                strbuf.append(count.numberOfFriends);
                strbuf.append(",");
                strbuf.append(count.numberOfPosts);
                strbuf.append(",");
                strbuf.append(count.numberOfLikes);
                strbuf.append(",");
                strbuf.append(count.numberOfTagsOfPosts);
                strbuf.append(",");
                strbuf.append(count.numberOfGroups);
                strbuf.append(",");
                strbuf.append(count.numberOfWorkPlaces);
                strbuf.append(",");
                strbuf.append(count.numberOfPostReplies);
                strbuf.append(",");

                int numBuckets = count.numberOfPostsPerMonth.length;
                for (int i = 0; i < numBuckets; i++) {
                    strbuf.append(count.numberOfPostsPerMonth[i]);
                    strbuf.append(",");
                }
                numBuckets = count.numberOfGroupsPerMonth.length;
                int total = count.numberOfGroupsPerMonth[0];
                strbuf.append(count.numberOfGroupsPerMonth[0]);
                for (int i = 1; i < numBuckets; i++) {
                    strbuf.append(",");
                    strbuf.append(count.numberOfGroupsPerMonth[i]);
                    total += count.numberOfGroupsPerMonth[i];
                }
                strbuf.append("\n");
                writer.write(strbuf.toString().getBytes());
            }
            writer.write(Integer.toString(postsPerCountry.size()).getBytes());
            writer.write("\n".getBytes());
            for (Map.Entry<Integer, Integer> c : postsPerCountry.entrySet()) {
                StringBuffer strbuf = new StringBuffer();
                strbuf.append(placeDictionary.getPlaceName(c.getKey()));
                strbuf.append(",");
                strbuf.append(c.getValue());
                strbuf.append("\n");
                writer.write(strbuf.toString().getBytes());
            }

            writer.write(Integer.toString(tagClassCount.size()).getBytes());
            writer.write("\n".getBytes());
            for (Map.Entry<Integer, Integer> c : tagClassCount.entrySet()) {
                StringBuffer strbuf = new StringBuffer();
                strbuf.append(tagDictionary.getClassName(c.getKey()));
                strbuf.append(",");
                strbuf.append(tagDictionary.getClassName(c.getKey()));
                strbuf.append(",");
                strbuf.append(c.getValue());
                strbuf.append("\n");
                writer.write(strbuf.toString().getBytes());
            }
            writer.write(Integer.toString(tagNameCount.size()).getBytes());
            writer.write("\n".getBytes());
            for (Map.Entry<Integer, Integer> c : tagNameCount.entrySet()) {
                StringBuffer strbuf = new StringBuffer();
                strbuf.append(tagDictionary.getName(c.getKey()));
                strbuf.append(",");
                //strbuf.append(tagDictionary.getClassName(c.getKey()));
                //strbuf.append(",");
                strbuf.append(c.getValue());
                strbuf.append("\n");
                writer.write(strbuf.toString().getBytes());
            }

            writer.write(Integer.toString(firstNameCount.size()).getBytes());
            writer.write("\n".getBytes());
            for (Map.Entry<String, Integer> c : firstNameCount.entrySet()) {
                StringBuffer strbuf = new StringBuffer();
                strbuf.append(c.getKey());
                strbuf.append(",");
                strbuf.append(c.getValue());
                strbuf.append("\n");
                writer.write(strbuf.toString().getBytes());
            }
            StringBuffer strbuf = new StringBuffer();
            strbuf.append(startMonth);
            strbuf.append("\n");
            strbuf.append(startYear);
            strbuf.append("\n");
            strbuf.append(stats.minWorkFrom);
            strbuf.append("\n");
            strbuf.append(stats.maxWorkFrom);
            strbuf.append("\n");
            writer.write(strbuf.toString().getBytes());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("Unable to write parameter counts");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeStatistics() {
        Gson gson = new GsonBuilder().setExclusionStrategies(stats.getExclusion()).disableHtmlEscaping().create();
        Configuration conf = new Configuration();
        try {
            FileSystem fs = FileSystem.get(conf);
            stats.makeCountryPairs(placeDictionary);
            stats.deltaTime = deltaTime;
            OutputStream writer = fs.create(new Path(params.outputDir + "/" + "m" + threadId + STATS_FILE));
            writer.write(gson.toJson(stats).getBytes("UTF8"));
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("Unable to write stastistics");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

}
