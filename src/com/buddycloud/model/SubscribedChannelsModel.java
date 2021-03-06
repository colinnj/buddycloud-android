package com.buddycloud.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import android.content.Context;

import com.buddycloud.http.BuddycloudHTTPHelper;
import com.buddycloud.model.dao.SubscribedChannelsDAO;
import com.buddycloud.preferences.Preferences;

public class SubscribedChannelsModel extends AbstractModel<JSONObject, JSONObject, String> {

	private static SubscribedChannelsModel instance;
	
	public static final String ROLE = "com.buddycloud.ROLE";
	public static final String ROLE_MEMBER = "member";
	public static final String ROLE_PUBLISHER = "publisher";
	public static final String ROLE_OUTCAST = "outcast";
	public static final String ROLE_MODERATOR = "moderator";
	public static final String ROLE_OWNER = "owner";
	
	public static final String SUBSCRIPTION_SUBSCRIBED = "subscribed";
	public static final String SUBSCRIPTION_PENDING = "pending";
	public static final String SUBSCRIPTION_NONE = "none";
	
	public static final String ACCESS_MODEL = "com.buddycloud.ACCESS_MODEL";
	public static final String ACCESS_MODEL_OPEN = "open";
	public static final String ACCESS_MODEL_AUTHORIZE = "authorize";
	
	public static final String POST_NODE_SUFIX = "/posts";
	
	private static final String ENDPOINT = "/subscribed"; 
	
	private SubscribedChannelsModel() {}
	
	public static SubscribedChannelsModel getInstance() {
		if (instance == null) {
			instance = new SubscribedChannelsModel();
		}
		return instance;
	}
	
	public void getFromServer(final Context context, final ModelCallback<JSONObject> callback, String... p) {
		
	}

	private static String url(Context context) {
		String apiAddress = Preferences.getPreference(context, Preferences.API_ADDRESS);
		return apiAddress + ENDPOINT;
	}


	@Override
	public void save(final Context context, JSONObject object,
			final ModelCallback<JSONObject> callback, String... p) {
		if (p.length > 0) {
			return;
		}
		
		try {
			StringEntity requestEntity = new StringEntity(object.toString(), "UTF-8");
			requestEntity.setContentType("application/json");
			BuddycloudHTTPHelper.post(url(context), true, false, requestEntity, context, new ModelCallback<JSONObject>() {
				@Override
				public void success(final JSONObject subscription) {
					fill(context, new ModelCallback<Void>() {
						@Override
						public void success(Void response) {
							callback.success(subscription);
						}

						@Override
						public void error(Throwable throwable) {
							callback.error(throwable);
						}
					});
				}
				
				@Override
				public void error(Throwable throwable) {
					callback.error(throwable);
				}
			});
		} catch (Exception e) {
			callback.error(e);
		}
	}

	@Override
	public JSONObject getFromCache(Context context, String... p) {
		JSONObject subscribed = SubscribedChannelsDAO.getInstance(context).get(null);
		if (subscribed == null) {
			return new JSONObject();
		}
		return subscribed;
	}

	@Override
	public void fill(final Context context, final ModelCallback<Void> callback, String... p) {
		BuddycloudHTTPHelper.getObject(url(context), context, 
				new ModelCallback<JSONObject>() {
					@SuppressWarnings("unchecked")
					@Override
					public void success(JSONObject response) {
						final Map<String, String> subscriptions = new HashMap<String, String>();
						Iterator<String> keyIterator = response.keys();
						while (keyIterator.hasNext()) {
							String node = (String) keyIterator.next();
							if (node.endsWith(POST_NODE_SUFIX)) {
								subscriptions.put(node.split("/")[0], 
										response.optString(node));
							}
						}
						
						SubscribedChannelsDAO dao = SubscribedChannelsDAO.getInstance(context);
						if (dao.get(null) == null) {
							dao.insert(null, new JSONObject(subscriptions));
						} else {
							dao.update(null, new JSONObject(subscriptions));
						}
						notifyChanged();
						callback.success(null);
					}
					
					@Override
					public void error(Throwable throwable) {
						callback.error(throwable);
					}
				});
	}

	public static boolean canEditMetadata(final String role) {
		return role != null && role.equals(ROLE_OWNER);
	}

	public static boolean canPost(String role) {
		if (role == null) {
			return false;
		}
		return role.equals(ROLE_OWNER)
				|| role.equals(ROLE_MODERATOR)
				|| role.equals(ROLE_PUBLISHER);
	}

	public static boolean canDeletePost(String role) {
		if (role == null) {
			return false;
		}
		return role.equals(ROLE_OWNER)
				|| role.equals(ROLE_MODERATOR);
	}
	
	public static boolean canDeleteChannel(String role) {
		if (role == null) {
			return false;
		}
		return role.equals(ROLE_OWNER);
	}
	
	public static boolean isFollowing(String role) {
		if (role == null) {
			return false;
		}
		return role.equals(ROLE_OWNER)
				|| role.equals(ROLE_MODERATOR)
				|| role.equals(ROLE_PUBLISHER)
				|| role.equals(ROLE_MEMBER);
	}
	
	public static boolean canMakeModerator(String role) {
		return role != null && role.equals(ROLE_OWNER);
	}

	public static boolean canChangeAffiliation(String role) {
		if (role == null) {
			return false;
		}
		return role.equals(ROLE_OWNER) 
				|| role.equals(ROLE_MODERATOR);
	}
	
	@Override
	public void delete(Context context, ModelCallback<Void> callback, String... p) {
		// TODO Auto-generated method stub
		
	}
}
