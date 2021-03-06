package DataAccessTests;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;


import java.util.*;

import redis.clients.jedis.BinaryJedis;
import services.dataAccess.AbstractDataAccess;
import services.dataAccess.RedisAccessObject;
import services.dataAccess.proto.PostProto.Post;
import services.dataAccess.proto.PostListProto.PostList;
import static services.dataAccess.TestDataGenerator.generateListOfPosts;

import static services.PublicConstants.DATA_SOURCE;
import static services.PublicConstants.REDIS_PORT;
import static services.PublicConstants.REDIS_URL;
import static services.dataAccess.TestDataGenerator.generatePostList;
import static services.dataAccess.TestDataGenerator.randomHashtags;


/**
 * <p>
 * Tests functionality of services.dataAccess.RedisAccessObject
 * on a real Redis instance.
 * <p>
 * In addition to the running Redis instance, the instance's
 * URL and port must be specified as environment variables:
 * <p>
 * redis_url=""
 * redis_port=""
 * <p>
 * data_source="redis"
 * must also be set.
 * <p>
 * Each test assumes boolean redisTestsIncluded is true; if false, no tests will run.
 */
public class RedisAccessTest {
    private static final Integer numTestPosts = 10;     // MUST be greater than 1.

    // generate unique ID within test namespace
    private static final String testKeyString = "test" + UUID.randomUUID().toString();
    private static boolean redisTestsIncluded = false;

    // Class under test
    private static String redisUrl = System.getenv(REDIS_URL);
    private static Integer redisPort = Integer.valueOf(System.getenv(REDIS_PORT));
    private static RedisAccessObject redisAccessObject;

    // direct connection to Redis for maintenance
    private static BinaryJedis directToRedis;

    @BeforeClass
    public static void redisTestSetUp() {

        // Initialize object under test and direct connection
        String dataSource = System.getenv(DATA_SOURCE);
        redisTestsIncluded = (dataSource != null && dataSource.equals("redis"));

        redisAccessObject = new RedisAccessObject();
        directToRedis = new BinaryJedis(redisUrl, redisPort);

        assert (numTestPosts > 1); // we must have more than one test post
    }

    // Delete all data from data store before and after each test
    @Before
    @After
    public void emptyRedisTestKey() {
        assumeTrue(redisTestsIncluded);

        directToRedis.connect();
        directToRedis.flushAll();
        directToRedis.disconnect();
    }

    /*
     * Post Management Tests
     */

    @Test
    public void testAddPostToEmpty() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // Add new post to completely empty store. Should create new key entry, with new post at front of key
        redisAccessObject.addNewPostFromSource(testKeyString, posts.get(0));
        assertEquals(posts.get(0), redisAccessObject.getAllPostsFromSource(testKeyString).get(0));
    }

    @Test
    public void testAddPostToNonEmpty() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // Initialize store with multiple posts, and add a single key
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);
        redisAccessObject.addNewPostFromSource(testKeyString, posts.get(0));

        // new post has been added to end of posts
        assertEquals(posts.get(0), redisAccessObject.getAllPostsFromSource(testKeyString).get(numTestPosts));
    }

    @Test
    public void testAddPostsToEmpty() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // add multiple posts to an empty key. Should create new key entry, with posts in order
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        assertEquals(posts, redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    @Test
    public void testAddPostsToNonEmpty() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // Add 2 sets of posts
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // new posts have been added in order and succeed old posts
        assertEquals(posts, redisAccessObject.getAllPostsFromSource(testKeyString).subList(numTestPosts, 2 * numTestPosts));
    }

    @Test
    public void testPopEmptyPost() {
        assumeTrue(redisTestsIncluded);

        // Need to see that we handle an empty response correctly
        assertFalse(redisAccessObject.popFirstPostFromSource(testKeyString).isPresent());
    }

    @Test
    public void testPopNonEmptyPost() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // add posts
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // see if we pop them in order
        for (int i = 0; i < numTestPosts; i++) {
            assertEquals(Optional.of(posts.get(i)), redisAccessObject.popFirstPostFromSource(testKeyString));
        }
    }

    @Test
    public void testAddListOfPostsToEmpty() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // add posts to empty keyspace; should create new keyspace with those posts in order
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);
        assertEquals(posts, redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    @Test
    public void testAddListOfOddOrderedPostsToNonEmpty() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // initialize temporary list with posts, and add one post at index 0
        ArrayList<Post> testList = new ArrayList<>(posts);
        testList.add(0, posts.get(0));  // add first element to beginning of test list

        // add first post to redis, then remaining posts
        redisAccessObject.addNewPostFromSource(testKeyString, posts.get(0));
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // check if posts were added in correct order
        assertEquals(testList, redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    @Test
    public void testGetEmptyListofPosts() {
        assumeTrue(redisTestsIncluded);

        // check for proper empty return value
        List<Post> emptyList = redisAccessObject.getAllPostsFromSource(testKeyString);
        assertEquals(new ArrayList<Post>(), emptyList);
    }

    @Test
    public void testGetNonEmptyListofPosts() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        // check for successful return, in-order
        redisAccessObject.addNewPostsFromSource(testKeyString, posts);
        assertEquals(posts, redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    /*
     * PostList Management Tests
     */

    @Test
    public void testPeekAtEmptyPostList() {
        assumeTrue(redisTestsIncluded);

        // check for error handling on empty PostList
        assertFalse(redisAccessObject.getDisplayPostList(testKeyString, 0).isPresent());
    }

    @Test
    public void testPeekAtNonEmptyPostList() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        // initialize postlist
        redisAccessObject.addNewDisplayPostList(testKeyString, postList);
        redisAccessObject.addNewDisplayPostList(testKeyString, postList);

        // get postlist; test twice to ensure we are not popping.
        assertEquals(Optional.of(postList), redisAccessObject.getDisplayPostList(testKeyString, 0));
        assertEquals(Optional.of(postList), redisAccessObject.getDisplayPostList(testKeyString, 0));
        assertEquals(Optional.of(postList), redisAccessObject.getDisplayPostList(testKeyString, 1));
        assertEquals(Optional.of(postList), redisAccessObject.getDisplayPostList(testKeyString, 1));
    }

    @Test
    public void testAddDisplayPostToEmpty() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        // add postList to empty keyspace; should create new keyspace with this postList at beginning
        redisAccessObject.addNewDisplayPostList(testKeyString, postList);
        assertEquals(Optional.of(postList), redisAccessObject.getDisplayPostList(testKeyString, 0));
    }

    @Test
    public void testAddHashTagPostListToEmpty() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        redisAccessObject.addNewHashTagPostList(testKeyString, postList);
        assertEquals(Optional.of(postList), redisAccessObject.getHashTagPostList(testKeyString, 0));
    }

    @Test
    public void testAddDisplayPostListToNonEmpty() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        redisAccessObject.addNewDisplayPostList(testKeyString, postList);
        redisAccessObject.addNewDisplayPostList(testKeyString, postList);
        assertEquals(Optional.of(postList), redisAccessObject.getDisplayPostList(testKeyString, 1));
    }

    @Test
    public void testAddHashTagPostListToNonEmpty() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        redisAccessObject.addNewHashTagPostList(testKeyString, postList);
        redisAccessObject.addNewHashTagPostList(testKeyString, postList);
        assertEquals(Optional.of(postList), redisAccessObject.getHashTagPostList(testKeyString, 1));
    }

    @Test
    public void testGetOutOfBoundsPostList() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        redisAccessObject.addNewDisplayPostList(testKeyString, postList);
        assertEquals(Optional.empty(), redisAccessObject.getDisplayPostList(testKeyString, Integer.MAX_VALUE));
    }

    /*
     * getNumPosts Tests
     */

    @Test
    public void testGetNumPostsInEmptyNameSpace() {
        assumeTrue(redisTestsIncluded);
        assertEquals(0, redisAccessObject.getNumPostsInNameSpace(""));
    }

    @Test
    public void testGetNumPostsInNonEmptyNameSpace() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);
        PostList postList = PostList.newBuilder().addAllPosts(posts).build();

        final String testKeyStringZero = testKeyString + "0";
        final String testKeyStringOne = testKeyString + "1";
        final String negativeTestKeyString = AbstractDataAccess.getTestNamespace();

        redisAccessObject.addNewPostsFromSource(testKeyString, posts);
        redisAccessObject.addNewPostsFromSource(testKeyStringZero, posts);
        redisAccessObject.addNewPostsFromSource(testKeyStringOne, posts);
        redisAccessObject.addNewDisplayPostList(negativeTestKeyString, postList);

        long numPostsInNameSpace = redisAccessObject.getNumPostsInNameSpace(AbstractDataAccess.getSourceNamespace());

        // clean up keys created locally by this test (not included in @After)
        directToRedis.connect();
        directToRedis.del((AbstractDataAccess.getSourceNamespace()
                + AbstractDataAccess.getNamespaceDelimiter()
                + testKeyStringZero).getBytes());
        directToRedis.del((AbstractDataAccess.getSourceNamespace()
                + AbstractDataAccess.getNamespaceDelimiter()
                + testKeyStringOne).getBytes());
        directToRedis.del((AbstractDataAccess.getDisplayNamespace()
                + AbstractDataAccess.getNamespaceDelimiter()
                + negativeTestKeyString).getBytes());
        directToRedis.disconnect();

        assertEquals(3 * numTestPosts, numPostsInNameSpace);

    }

    /*
     * getKeysInNameSpace Tests
     */

    @Test
    public void testGetKeysInEmptyNameSpace() {
        assumeTrue(redisTestsIncluded);

        // result on query for non-existent keys should be empty
        assertEquals(Collections.emptyList(), redisAccessObject.getKeysInNameSpace(AbstractDataAccess.getTestNamespace()));
    }

    @Test
    public void testGetKeysInNonEmptyNameSpace() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // should return list containing only testKeyString
        assertEquals(AbstractDataAccess.getSourceNamespace()
                + AbstractDataAccess.getNamespaceDelimiter()
                + testKeyString,
                redisAccessObject.getKeysInNameSpace(AbstractDataAccess.getSourceNamespace()).get(0));
    }

    /*
     * deleteNPosts Tests
     */

    @Test
    public void testDeleteNPostsFromNonEmptyListOfPosts() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // delete first 5 posts in testKeyString, check that what remains matches the subList at index 5 and beyond
        redisAccessObject.deleteFirstNPostsFromSourceQueue(testKeyString, 5);
        assertEquals(posts.subList(5, numTestPosts), redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    @Test
    public void testDeleteNPostsFromEmptyListOfPosts() {
        assumeTrue(redisTestsIncluded);

        // delete on empty should result in empty.
        redisAccessObject.deleteFirstNPostsFromSourceQueue(testKeyString, 1);
        assertEquals(Collections.emptyList(), redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    @Test
    public void testDeleteMoreThanSizePostsFromListOfPosts() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // deleting more than size posts should result in empty list at keyString
        redisAccessObject.deleteFirstNPostsFromSourceQueue(testKeyString, numTestPosts + 1);
        assertEquals(Collections.emptyList(), redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    @Test
    public void testDeleteZeroPostsFromListOfPosts() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);

        redisAccessObject.addNewPostsFromSource(testKeyString, posts);

        // calling delete on 0 keys should not change list
        redisAccessObject.deleteFirstNPostsFromSourceQueue(testKeyString, 0);
        assertEquals(posts, redisAccessObject.getAllPostsFromSource(testKeyString));
    }

    /*
     * Expiry Tests
     */

    @Deprecated // deprecated due to incredibly long runtime (should be verified periodically in production)
    public void testPostListExpiry() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        // load list at testKeyString past the maximum allocated number of postLists
        for (int i = 0; i < AbstractDataAccess.getMaxPostlists() + numTestPosts; i++) {
            redisAccessObject.addNewHashTagPostList(testKeyString, postList);
        }

        // check that the number of postLists has met and not exceeded the maximum
        directToRedis.connect();
        Long response = directToRedis.llen((AbstractDataAccess.getHashtagNamespace()
                + AbstractDataAccess.getNamespaceDelimiter()
                + testKeyString).getBytes());
        directToRedis.disconnect();

        assertTrue(response.equals(AbstractDataAccess.getMaxPostlists().longValue()));

    }

    /*
       replaceHashTagPostLists tests
     */

    @Test
    public void testHashTagPostListReplaceSingle() {
        assumeTrue(redisTestsIncluded);

        List<Post> posts = generateListOfPosts(numTestPosts);
        PostList postList = PostList.newBuilder().addAllPosts(posts).build();

        // build list containing one reversed postList
        PostList reversePostList = PostList.newBuilder().addAllPosts(Lists.reverse(posts)).build();
        List<PostList> postLists = new ArrayList<>();
        postLists.add(reversePostList);

        // add postList and replace it with the reverse
        redisAccessObject.addNewHashTagPostList(testKeyString, postList);
        redisAccessObject.replaceHashTagPostLists(testKeyString, postLists);

        // only reversed postList should remain
        assertEquals(Optional.of(reversePostList), redisAccessObject.getHashTagPostList(testKeyString, 0));
        assertEquals(Optional.empty(), redisAccessObject.getHashTagPostList(testKeyString, 1));
    }

    @Test
    public void testHashTagPostListReplaceEmptyList() {
        assumeTrue(redisTestsIncluded);

        PostList postList = generatePostList(numTestPosts);

        // add postList to hashtag channel, and replace with empty postlist
        redisAccessObject.addNewHashTagPostList(testKeyString, postList);
        redisAccessObject.replaceHashTagPostLists(testKeyString, Collections.emptyList());

        // no postLists should exist
        assertEquals(Optional.empty(), redisAccessObject.getHashTagPostList(testKeyString, 0));
        assertEquals(Optional.empty(), redisAccessObject.getHashTagPostList(testKeyString, 1));
    }

    @Test
    public void testHashTagPostListReplace() {
        assumeTrue(redisTestsIncluded);
        int numPostLists = numTestPosts;
        assert(numPostLists < AbstractDataAccess.getMaxPostlists());    // numPostLists must be less than max allowed

        List<Post> posts = generateListOfPosts(numTestPosts);

        // create list of PostLists, add in increasing size
        List<PostList> postLists = new ArrayList<>();
        for (int i = 0; i < numPostLists; i++) {
            postLists.add(PostList.newBuilder().addAllPosts(posts.subList(0, i+1)).build());
        }

        // replace empty channel with list of postLists
        redisAccessObject.replaceHashTagPostLists(testKeyString, postLists);

        // postsLists list should be entered in order
        for (int i = 0; i < numPostLists; i++) {
            assertEquals(Optional.of(postLists.get(i)), redisAccessObject.getHashTagPostList(testKeyString, i));
        }
    }


    /*
       addTopHashtags tests
     */

    @Test
    public void testAddTopHashtagsEmptyList() {
        assumeTrue(redisTestsIncluded);

        List<String> hashtags = randomHashtags();
        int numTags = hashtags.size();

        // add hashtags, then empty list. Result should be no entries in channel
        redisAccessObject.addTopHashtags(hashtags);
        redisAccessObject.addTopHashtags(Collections.emptyList());
        assertEquals(Collections.emptyList(), redisAccessObject.getTopHashTags(numTags));
    }

    @Test
    public void testAddTopHashtagsEmptyChannel() {
        assumeTrue(redisTestsIncluded);
        List<String> hashtags = randomHashtags();
        int numTags = hashtags.size();

        // add list of hashtags to channel; they should then be stored in order
        redisAccessObject.addTopHashtags(hashtags);
        assertEquals(hashtags, redisAccessObject.getTopHashTags(numTags));
    }

    @Test
    public void testAddTopHashtags() {
        assumeTrue(redisTestsIncluded);

        List<String> hashtags = randomHashtags();
        List<String> reversedHashtags = Lists.reverse(hashtags);
        int numTags = hashtags.size();

        // add hashtags, then reversed hashtags. Reversed hashtags should remain.
        redisAccessObject.addTopHashtags(hashtags);
        redisAccessObject.addTopHashtags(reversedHashtags);
        assertEquals(reversedHashtags, redisAccessObject.getTopHashTags(numTags));
    }

}
