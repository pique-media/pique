package services.sources;

import services.dataAccess.proto.PostProto;
import services.dataAccess.proto.PostProto.Post;
import twitter4j.HashtagEntity;
import twitter4j.Location;
import twitter4j.MediaEntity;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Trend;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.URLEntity;


import javax.xml.ws.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


/**
 * Class that interacts with the twitter4j library to get data
 *
 * @author Reid Oliveira, Sammie Jiang
 */
public class TwitterSource extends AbstractJavaSource {
    
    public static final String TWITTER = "twitter";
    public static final String DEFAULT_TEXT = "N/A";
    
	Map<String, Integer> cachedCodes = new HashMap<>();

	// twitter object that acts as the router for all requests
	Twitter twitter;

	public TwitterSource() {
		super(TWITTER);
		twitter = new TwitterFactory().getInstance();
	}

	/**
	 * Gets tweets corresponding to the current trending topics on Twitter
	 * @return
	 */
	@Override
	public List<PostProto.Post> getTopTrending() {
		// TODO get country code from FE request
		Optional<Trends> trends = getTrends("Canada");
		QueryResult result = null;

		if (trends.isPresent()) {
			for (Trend t : trends.get().getTrends()) {
				Query trendQuery = new Query(t.getQuery());
				trendQuery.setCount(100);

				try {
					result = twitter.search(trendQuery);
				} catch (Exception e) {
					e.printStackTrace();
					// TODO
				}
			}
		}

		return parseQueryResult(result);
	}

	/**
	 * Generates Post object for every status in given queried tweets
	 * @param result
	 * @return
	 */
	private List<PostProto.Post> parseQueryResult(QueryResult result) {
		if (result.getTweets() == null || result.getTweets().isEmpty())
			return Collections.emptyList();

		List<PostProto.Post> posts = new ArrayList<>();
		
		for (Status s : result.getTweets()) {

			PostProto.Post.Builder builder = PostProto.Post.newBuilder();
			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

			setPostVariables(s, builder);

			posts.add(builder.build());
			
		}

		return posts;
	}

	private void setPostVariables(Status s, PostProto.Post.Builder builder) {
		builder.setId(String.valueOf(s.getId()));
		builder.setTimestamp(df.format(s.getCreatedAt()));
		builder.setSource(0, s.getUser().getScreenName());
		builder.setSourceLink(0, s.getUser().getURL().toString());
		builder.setPopularityScore(0); //TODO
		builder.setPopularityVelocity(0); //TODO
		builder.setNumComments(0); //TODO or maybe not possible
		setPostNumShares(s, builder);
		builder.setNumLikes(s.getFavoriteCount());
		builder.setText(0, s.getText());
		setPostHashtag(s, builder);
		setPostMedia(s, builder);
		setPostURL(s, builder);
	}

	private void setPostNumShares(Status s, PostProto.Post.Builder builder) {
		if (s.getRetweetCount() == -1) { //If tweet was created before the retween count feature was enabled
			//TODO
		} else {
			builder.setNumShares(s.getRetweetCount());
		}
	}

	private void setPostHashtag(Status s, PostProto.Post.Builder builder) {
		HashtagEntity[] hashtagArray = s.getHashtagEntities();
		if (hashtagArray == null) {
			builder.setHashtag(0, DEFAULT_TEXT);
		} else {
			for (int i = 0; i < hashtagArray.length; i++) {
				builder.setHashtag(i, hashtagArray[i].getText());
			}
		}
	}

	private void setPostMedia(Status s, PostProto.Post.Builder builder) {
		MediaEntity[] mediaArray = s.getMediaEntities();
		if (mediaArray == null) {
			builder.setImgLink(0, DEFAULT_TEXT);
		} else {
			for (int j = 0; j < mediaArray.length; j++) {
				builder.setImgLink(j, mediaArray[j].getMediaURL());
			}
		}
	}

	private void setPostURL(Status s, PostProto.Post.Builder builder) {
		URLEntity[] urlArray = s.getURLEntities();
		if (urlArray == null) {
			builder.setExtLink(0, DEFAULT_TEXT);
		} else {
			for (int k = 0; k < urlArray.length; k++) {
				builder.setExtLink(k, urlArray[k].getURL());
			}
		}
	}






	/**
	 * Gets the {@link Trends} for a certain country, if it is available.
	 * @param country the country to get {@link Trends} for
	 * @return an {@link Optional} containing the trends, or an empty {@link Optional} if
	 * {@link Trends} are not available for that country.
	 */
	private Optional<Trends> getTrends(String country){

		try{
			Optional<Integer> id = getLocationId(country);

			if (id.isPresent()) {
				return Optional.of(twitter.getPlaceTrends(id.get()));
			}
		} catch (Exception e){
			e.printStackTrace();
			// TODO
		}

		return Optional.empty();
	}

	/**
	 * Retrives the {@link Location} object for the specified country if it is available,
	 * else an empty Optional.
	 *
	 * @param country the name of the country
	 * @return {@link Optional} of the {@link Location}, or null if it is not available.
	 */
	private Optional<Integer> getLocationId(String country) {
		String countryLower = country.toLowerCase();
		if (cachedCodes.containsKey(countryLower)) {
			return Optional.of(cachedCodes.get(countryLower));
		}

		try {
			ResponseList<Location> availableLocations = twitter.getAvailableTrends();
			for (Location l : availableLocations) {
				if (l.getCountryName().equalsIgnoreCase(countryLower)) {
					cachedCodes.put(countryLower, l.getWoeid());
					return Optional.of(l.getWoeid());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			// TODO
		}

		return Optional.empty(); // could not find the location
	}

}
