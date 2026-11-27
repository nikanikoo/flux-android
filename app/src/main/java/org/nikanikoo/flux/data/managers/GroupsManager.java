package org.nikanikoo.flux.data.managers;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Friend;
import org.nikanikoo.flux.data.models.Group;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupsManager extends BaseManager<GroupsManager> {
    private static final String TAG = "GroupsManager";

    private GroupsManager(Context context) {
        super(context);
    }

    public static synchronized GroupsManager getInstance(Context context) {
        return BaseManager.getInstance(GroupsManager.class, context);
    }

    public interface GroupsCallback {
        void onSuccess(List<Group> groups);
        void onError(String error);
    }

    public interface GroupCallback {
        void onSuccess(Group group);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface MembersCallback {
        void onSuccess(List<Friend> members);
        void onError(String error);
    }

    // Получение списка групп пользователя
    public void getGroups(int count, int offset, GroupsCallback callback) {
        getGroups(count, offset, "groups", callback);
    }

    public void getAdminGroups(GroupsCallback callback) {
        getGroups(100, 0, "admin", callback);
    }

    private void getGroups(int count, int offset, String filter, GroupsCallback callback) {
        Logger.d(TAG, "Starting getGroups request...");
        
        // Ограничиваем количество для предотвращения OutOfMemory
        int safeCount = Math.min(count, "admin".equals(filter) ? 100 : Constants.Api.FRIENDS_PER_PAGE);
        
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(safeCount));
        params.put("offset", String.valueOf(offset));
        params.put("filter", filter);
        params.put("fields", "photo_50,photo_100,photo_200,description,members_count,is_closed,is_admin,is_member,can_post,type,activity,status,verified,website,city,country");
        params.put("extended", "1");

        Logger.d(TAG, "Request params: " + params.toString());

        api.callMethod("groups.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.d(TAG, "Raw response: " + response.toString());
                    
                    if (response.has("response")) {
                        JSONObject responseObj = response.getJSONObject("response");
                        
                        if (responseObj.has("items")) {
                            JSONArray items = responseObj.getJSONArray("items");
                            Logger.d(TAG, "Found " + items.length() + " groups in items array");
                            parseGroupsFromArray(items, callback);
                        } else if (responseObj.has("count")) {
                            int totalCount = responseObj.getInt("count");
                            Logger.d(TAG, "Total groups count: " + totalCount);
                            
                            if (responseObj.has("items")) {
                                JSONArray items = responseObj.getJSONArray("items");
                                parseGroupsFromArray(items, callback);
                            } else {
                                callback.onError("Нет массива items в ответе");
                            }
                        } else {
                            try {
                                JSONArray groupsArray = response.getJSONArray("response");
                                Logger.d(TAG, "Found " + groupsArray.length() + " groups in direct array");
                                parseGroupsFromArray(groupsArray, callback);
                            } catch (Exception e) {
                                Logger.e(TAG, "Error parsing direct array: " + e.getMessage(), e);
                                callback.onError("Неожиданная структура ответа");
                            }
                        }
                    } else {
                        callback.onError("Нет поля response в ответе");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing response: " + e.getMessage(), e);
                    callback.onError("Не удалось загрузить список групп");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "API error: " + error);
                callback.onError("Не удалось загрузить список групп");
            }
        });
    }

    // Получение информации о конкретной группе
    public void getGroupById(int groupId, GroupCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("group_ids", String.valueOf(groupId));
        params.put("fields", "photo_50,photo_100,photo_200,description,members_count,is_closed,is_admin,is_member,can_post,type,activity,status,verified,website,city,country");

        api.callMethod("groups.getById", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray groups = response.getJSONArray("response");
                    if (groups.length() > 0) {
                        JSONObject groupJson = groups.getJSONObject(0);
                        Group group = Group.fromJson(groupJson);
                        callback.onSuccess(group);
                    } else {
                        callback.onError("Группа не найдена");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing group by id", e);
                    callback.onError("Не удалось загрузить информацию о группе");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error getting group by id: " + error);
                callback.onError("Не удалось загрузить информацию о группе");
            }
        });
    }

    // Вступление в группу
    public void joinGroup(int groupId, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("group_id", String.valueOf(groupId));

        api.callMethod("groups.join", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error joining group: " + error);
                callback.onError("Не удалось вступить в группу");
            }
        });
    }

    // Выход из группы
    public void leaveGroup(int groupId, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("group_id", String.valueOf(groupId));

        api.callMethod("groups.leave", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error leaving group: " + error);
                callback.onError("Не удалось выйти из группы");
            }
        });
    }
    
    // Получение участников группы
    public void getMembers(int groupId, int count, int offset, MembersCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("group_id", String.valueOf(groupId));
        params.put("count", String.valueOf(count));
        params.put("offset", String.valueOf(offset));
        params.put("fields", "photo_50,photo_100,online,screen_name,status");
        params.put("sort", "id_asc");

        api.callMethod("groups.getMembers", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    
                    List<Friend> members = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject memberJson = items.getJSONObject(i);
                        Friend member = Friend.fromJson(memberJson);
                        members.add(member);
                    }
                    
                    callback.onSuccess(members);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing group members", e);
                    callback.onError("Не удалось загрузить участников группы");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error getting group members: " + error);
                callback.onError("Не удалось загрузить участников группы");
            }
        });
    }

    private void parseGroupsFromArray(JSONArray groupsArray, GroupsCallback callback) {
        try {
            List<Group> groups = new ArrayList<>();
            
            if (groupsArray == null) {
                callback.onSuccess(groups);
                return;
            }
            
            int maxGroups = Math.min(groupsArray.length(), 100); // Ограничиваем количество
            
            for (int i = 0; i < maxGroups; i++) {
                try {
                    JSONObject groupJson = groupsArray.getJSONObject(i);
                    if (groupJson != null) {
                        Group group = Group.fromJson(groupJson);
                        if (group != null) {
                            groups.add(group);
                        }
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing group " + i + ": " + e.getMessage(), e);
                    // Продолжаем обработку остальных групп
                }
            }
            
            Logger.d(TAG, "Successfully parsed " + groups.size() + " groups");
            callback.onSuccess(groups);
            
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing groups array: " + e.getMessage(), e);
            callback.onError("Не удалось загрузить список групп");
        }
    }
}
