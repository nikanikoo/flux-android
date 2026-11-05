package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Sticker;
import org.nikanikoo.flux.data.models.StickerPack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StickersManager extends BaseManager<StickersManager> {
    private StickersManager(Context context) {
        super(context);
    }

    public static StickersManager getInstance(Context context) {
        return BaseManager.getInstance(StickersManager.class, context);
    }

    public interface StickersCallback {
        void onSuccess(List<Sticker> stickers);
        void onError(String error);
    }

    public interface StickerPacksCallback {
        void onSuccess(List<StickerPack> packs);
        void onError(String error);
    }

    public interface PurchaseCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getAllPacks(StickerPacksCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", "100");
        params.put("offset", "0");
        api.callMethod("stickers.getAll", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray items = response.getJSONObject("response").optJSONArray("items");
                    List<StickerPack> packs = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;
                            int id = item.optInt("id", 0);
                            if (id <= 0) continue;
                            packs.add(new StickerPack(id,
                                    item.optString("title", item.optString("name", "")),
                                    item.optString("description", ""),
                                    item.optInt("price", 0),
                                    item.optInt("purchased", 0) == 1,
                                    item.optLong("end_time", 0) == 0
                                            || item.optLong("end_time", 0) > System.currentTimeMillis() / 1000,
                                    item.optString("photo_128", "")));
                        }
                    }
                    callback.onSuccess(packs);
                } catch (Exception e) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Could not read sticker packs");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void buyPack(int packId, PurchaseCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("stickerpack_id", String.valueOf(packId));
        api.callMethod("stickers.buy", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject result = response.optJSONObject("response");
                if (result != null && result.optInt("success", 0) == 1) {
                    callback.onSuccess();
                } else {
                    callback.onError("Could not purchase sticker pack");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getOwnedStickers(StickersCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", "100");
        params.put("offset", "0");

        api.callMethod("stickers.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObject = response.getJSONObject("response");
                    JSONArray packs = responseObject.optJSONArray("items");
                    List<Sticker> result = new ArrayList<>();
                    if (packs != null) {
                        for (int i = 0; i < packs.length(); i++) {
                            JSONObject pack = packs.optJSONObject(i);
                            if (pack == null) continue;
                            String packName = pack.optString("title", pack.optString("name", ""));
                            JSONArray packStickers = pack.optJSONArray("stickers");
                            if (packStickers == null) continue;
                            for (int j = 0; j < packStickers.length(); j++) {
                                JSONObject item = packStickers.optJSONObject(j);
                                if (item == null) continue;
                                int id = item.optInt("sticker_id", item.optInt("id", 0));
                                String imageUrl = item.optString("photo_128", "");
                                if (imageUrl.isEmpty()) imageUrl = item.optString("photo_256", "");
                                if (imageUrl.isEmpty()) {
                                    JSONArray images = item.optJSONArray("images");
                                    if (images != null && images.length() > 0) {
                                        JSONObject image = images.optJSONObject(Math.min(1, images.length() - 1));
                                        if (image != null) imageUrl = image.optString("url", "");
                                    }
                                }
                                if (id > 0 && !imageUrl.isEmpty()) {
                                    result.add(new Sticker(id, packName, imageUrl, item.optString("emoji", "")));
                                }
                            }
                        }
                    }
                    callback.onSuccess(result);
                } catch (Exception e) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Could not read stickers");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
