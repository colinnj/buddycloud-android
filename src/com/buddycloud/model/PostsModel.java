package com.buddycloud.model;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

import com.buddycloud.http.BuddycloudHTTPHelper;
import com.buddycloud.model.dao.DAOCallback;
import com.buddycloud.model.dao.PostsDAO;
import com.buddycloud.preferences.Preferences;

public class PostsModel implements Model<JSONArray, JSONObject, String> {

	private static final String TAG = PostsModel.class.getName();
	private static PostsModel instance;
	private static final int PAGE_SIZE = 31;
	private static final String POSTS_ENDPOINT = "/content/posts";
	
	private Map<String, JSONArray> channelsPosts = new HashMap<String, JSONArray>();
	private Map<String, JSONArray> postsComments = new HashMap<String, JSONArray>();
	
	private PostsModel() {}

	public static PostsModel getInstance() {
		if (instance == null) {
			instance = new PostsModel();
		}
		return instance;
	}
	
	
	private boolean isPost(JSONObject item) {
		return item.opt("replyTo") == null;
	}
	
	private void parse(PostsDAO postsDAO, String channelJid, JSONArray response, boolean updateDatabase) {
		parseChannelPosts(postsDAO, channelJid, response, updateDatabase);
	}
	
	public void parseChannelPosts(PostsDAO postsDAO, String channel, JSONArray jsonPosts, boolean updateDatabase) {
		JSONArray posts = channelsPosts.get(channel);
		if (posts == null) {
			posts = new JSONArray();
		}
		
		for (int i = 0; i < jsonPosts.length(); i++) {
			JSONObject item = jsonPosts.optJSONObject(i);
			String author = item.optString("author");
			
			if (author.contains("acct:")) {
				String[] split = author.split(":");
				author = split[1];
				
				try {
					item.put("author", author);
				} catch (JSONException e) {}
			}
			
			if (updateDatabase) {
				postsDAO.insert(channel, item);
			}
			
			if (isPost(item)) {
				posts.put(item);
			} else {
				String postId = item.optString("replyTo");
				JSONArray comments = postsComments.get(postId);
				if (comments == null) {
					comments = new JSONArray();
				}
				
				comments.put(item);
				postsComments.put(postId, comments);
			}
		}
		
		channelsPosts.put(channel, posts);
	}
	
	private void lookupPostsFromDatabase(final Context context, 
			final String channelJid, 
			final ModelCallback<JSONArray> callback) {
		final PostsDAO postsDAO = PostsDAO.getInstance(context);
		
		DAOCallback<JSONArray> postCallback = new DAOCallback<JSONArray>() {
			@Override
			public void onResponse(JSONArray response) {
				if (response != null && response.length() > 0) {
					parseChannelPosts(postsDAO, channelJid, response, false);
				}
				fetchPosts(context, channelJid, callback);
			}
		};
		postsDAO.get(channelJid, PAGE_SIZE, postCallback);
	}
	
	@Override
	public void refresh(Context context, final ModelCallback<JSONArray> callback, String... p) {
		String channelJid = p[0];
		expire(channelJid);
		lookupPostsFromDatabase(context, channelJid, callback);
	}

	private void fetchPosts(Context context, String channelJid, final ModelCallback<JSONArray> callback) {
		sync(context, channelJid, callback);
	}
	
	private void sync(final Context context, final String channelJid, 
			final ModelCallback<JSONArray> callback) {
		BuddycloudHTTPHelper.getArray(postsUrl(context, channelJid), context,
				new ModelCallback<JSONArray>() {

			@Override
			public void success(JSONArray response) {
				final PostsDAO postsDAO = PostsDAO.getInstance(context);
				parse(postsDAO, channelJid, response, true);
				if (callback != null) {
					callback.success(response);
				}
			}

			@Override
			public void error(Throwable throwable) {
				if (callback != null) {
					callback.error(throwable);
				}
			}
		});
	}
	
	private String postsUrl(Context context, String channel) {
		String apiAddress = Preferences.getPreference(context, Preferences.API_ADDRESS);
		return apiAddress + "/" + channel + POSTS_ENDPOINT;
	}

	@Override
	public void save(Context context, JSONObject object,
			ModelCallback<JSONObject> callback, String... p) {
		if (p == null || p.length < 1) {
			return;
		}
		
		try {
			Log.d(TAG, object.toString());
			StringEntity requestEntity = new StringEntity(object.toString(), "UTF-8");
			requestEntity.setContentType("application/json");
			BuddycloudHTTPHelper.post(postsUrl(context, p[0]), true, false, requestEntity, context, callback);
		} catch (UnsupportedEncodingException e) {
			callback.error(e);
		}
	}

	@Override
	public JSONArray get(Context context, String... p) {
		if (p != null && p.length == 1) {
			String channelJid = p[0];
			if (channelsPosts.containsKey(channelJid)) {
				return channelsPosts.get(channelJid);
			}
		}
		return new JSONArray();
	}
	
	public JSONArray cachedPostsFromChannel(String channel) {
		if (channel != null) {
			if (channelsPosts.containsKey(channel)) {
				return channelsPosts.get(channel);
			}
		}
		
		return new JSONArray();
	}
	
	public Set<String> cachedChannels() {
		return channelsPosts.keySet();
	}
	
	public void expire() {
		channelsPosts.clear();
		postsComments.clear();
	}
	
	public void expire(String channelJid) {
		Iterator<Entry<String, JSONArray>> iterator = postsComments.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, JSONArray> entry = iterator.next();
			String postId = entry.getKey();
			JSONObject postWithId = postWithId(postId, channelJid);
			if (postWithId != null) {
				iterator.remove();
			}
		}
		channelsPosts.remove(channelJid);
	}
	
	public JSONArray cachedCommentsFromPost(String postId) {
		if (postId != null) {
			if (postsComments.containsKey(postId)) {
				return postsComments.get(postId);
			}
		}
		
		return new JSONArray();
	}
	
	public JSONObject postWithId(String postId, String channel) {
		JSONArray jsonArray = cachedPostsFromChannel(channel);
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject post = jsonArray.optJSONObject(i);
			if (post.optString("id").equals(postId)) {
				return post;
			}
		}
		return new JSONObject();
	}
	
	public void selectChannel(Context context, String channel) {
		
	}
}