package com.android.plugins;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.PowerManager;
import static android.content.Context.POWER_SERVICE;


/**
 * Created by JasonYang on 2016/3/11.
 */
public class Permissions extends CordovaPlugin {

    private static String TAG = "Permissions";

    private static final String ACTION_CHECK_PERMISSION = "checkPermission";
    private static final String ACTION_REQUEST_PERMISSION = "requestPermission";
    private static final String ACTION_REQUEST_PERMISSIONS = "requestPermissions";

    private static final int REQUEST_CODE_ENABLE_PERMISSION = 55433;
    private static int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 5469; // For SYSTEM_ALERT_WINDOW

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_RESULT_PERMISSION = "hasPermission";

    private CallbackContext permissionsCallback;

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (ACTION_CHECK_PERMISSION.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    checkPermissionAction(callbackContext, args);
                }
            });
            return true;
        } else if (ACTION_REQUEST_PERMISSION.equals(action) || ACTION_REQUEST_PERMISSIONS.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        requestPermissionAction(callbackContext, args);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JSONObject returnObj = new JSONObject();
                        addProperty(returnObj, KEY_ERROR, ACTION_REQUEST_PERMISSION);
                        addProperty(returnObj, KEY_MESSAGE, "Request permission has been denied.");
                        callbackContext.error(returnObj);
                        permissionsCallback = null;
                    }
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (permissionsCallback == null) {
            return;
        }

        JSONObject returnObj = new JSONObject();
        if (permissions != null && permissions.length > 0) {
            //Call checkPermission again to verify
            boolean hasAllPermissions = hasAllPermissions(permissions);
            addProperty(returnObj, KEY_RESULT_PERMISSION, hasAllPermissions);
            permissionsCallback.success(returnObj);
        } else {
            addProperty(returnObj, KEY_ERROR, ACTION_REQUEST_PERMISSION);
            addProperty(returnObj, KEY_MESSAGE, "Unknown error.");
            permissionsCallback.error(returnObj);
        }
        permissionsCallback = null;
    }

    private void checkPermissionAction(CallbackContext callbackContext, JSONArray permission) {
        if (permission == null || permission.length() == 0 || permission.length() > 1) {
            JSONObject returnObj = new JSONObject();
            addProperty(returnObj, KEY_ERROR, ACTION_CHECK_PERMISSION);
            addProperty(returnObj, KEY_MESSAGE, "One time one permission only.");
            callbackContext.error(returnObj);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            JSONObject returnObj = new JSONObject();
            addProperty(returnObj, KEY_RESULT_PERMISSION, true);
            callbackContext.success(returnObj);
        } else {
            String permission0;
            try {
                permission0 = permission.getString(0);
            } catch (JSONException ex) {
                JSONObject returnObj = new JSONObject();
                addProperty(returnObj, KEY_ERROR, ACTION_REQUEST_PERMISSION);
                addProperty(returnObj, KEY_MESSAGE, "Check permission has been failed." + ex);
                callbackContext.error(returnObj);
                return;
            }
            JSONObject returnObj = new JSONObject();
            if ("android.permission.SYSTEM_ALERT_WINDOW".equals(permission0)) {
                Context context = this.cordova.getActivity().getApplicationContext();
                addProperty(returnObj, KEY_RESULT_PERMISSION, Settings.canDrawOverlays(context));
            }else if ("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS".equals(permission0)) {
                Context context = this.cordova.getActivity().getApplicationContext();
                Activity activity = this.cordova.getActivity();
                PowerManager pm   = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                addProperty(returnObj, KEY_RESULT_PERMISSION, pm.isIgnoringBatteryOptimizations(activity.getPackageName()));
            } else {
                addProperty(returnObj, KEY_RESULT_PERMISSION, cordova.hasPermission(permission0));
            }
            callbackContext.success(returnObj);
        }
    }

    private void requestPermissionAction(CallbackContext callbackContext, JSONArray permissions) throws Exception {
        String[] permissionArray = getPermissions(permissions);
        if (permissions == null || permissions.length() == 0) {
            JSONObject returnObj = new JSONObject();
            addProperty(returnObj, KEY_ERROR, ACTION_REQUEST_PERMISSION);
            addProperty(returnObj, KEY_MESSAGE, "At least one permission.");
            callbackContext.error(returnObj);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            JSONObject returnObj = new JSONObject();
            addProperty(returnObj, KEY_RESULT_PERMISSION, true);
            callbackContext.success(returnObj);
        } else if ( permissionArray.length == 1 && "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS".equals(permissionArray[0])) {
            Log.i(TAG, "Request permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
            //hasAllPermissions(permissions)아래에서 하면 도달 못함 
            Activity activity = this.cordova.getActivity();
            PowerManager pm   = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            //if need trigger https://github.com/mauron85/cordova-plugin-background-geolocation/pull/594
            if (pm.isIgnoringBatteryOptimizations(activity.getPackageName()))
                return;

            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return;
        } else if (hasAllPermissions(permissions)) {
            JSONObject returnObj = new JSONObject();
            addProperty(returnObj, KEY_RESULT_PERMISSION, true);
            callbackContext.success(returnObj);
        } else {
            permissionsCallback = callbackContext;
            if (permissionArray.length == 1 && "android.permission.SYSTEM_ALERT_WINDOW".equals(permissionArray[0])) {
                Log.i(TAG, "Request permission SYSTEM_ALERT_WINDOW");

                Activity activity = this.cordova.getActivity();
                Context context = this.cordova.getActivity().getApplicationContext();

                // SYSTEM_ALERT_WINDOW
                // https://stackoverflow.com/questions/40355344/how-to-programmatically-grant-the-draw-over-other-apps-permission-in-android
                // https://www.codeproject.com/Tips/1056871/Android-Marshmallow-Overlay-Permission
                if (!Settings.canDrawOverlays(context)) {
                    Log.w(TAG, "Request permission SYSTEM_ALERT_WINDOW start intent because canDrawOverlays=false");
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                    return;
                }
            }
            // if (permissionArray.length == 1 && "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS".equals(permissionArray[0])) {
            //     Log.i(TAG, "Request permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");

            //     Activity activity = this.cordova.getActivity();
            //     PowerManager pm   = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            //     //if need trigger https://github.com/mauron85/cordova-plugin-background-geolocation/pull/594
            //     if (pm.isIgnoringBatteryOptimizations(activity.getPackageName()))
            //         return;

            //     Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            //             Uri.parse("package:" + activity.getPackageName()));
            //     activity.startActivity(intent);
            //     return;
            // }
            cordova.requestPermissions(this, REQUEST_CODE_ENABLE_PERMISSION, permissionArray);
        }
    }

    private String[] getPermissions(JSONArray permissions) {
        String[] stringArray = new String[permissions.length()];
        for (int i = 0; i < permissions.length(); i++) {
            try {
                stringArray[i] = permissions.getString(i);
            } catch (JSONException ignored) {
                //Believe exception only occurs when adding duplicate keys, so just ignore it
            }
        }
        return stringArray;
    }

    private boolean hasAllPermissions(JSONArray permissions) throws JSONException {
        return hasAllPermissions(getPermissions(permissions));
    }

    private boolean hasAllPermissions(String[] permissions) throws JSONException {

        for (String permission : permissions) {
            if(!cordova.hasPermission(permission)) {
                return false;
            }
        }

        return true;
    }

    private void addProperty(JSONObject obj, String key, Object value) {
        try {
            if (value == null) {
                obj.put(key, JSONObject.NULL);
            } else {
                obj.put(key, value);
            }
        } catch (JSONException ignored) {
            //Believe exception only occurs when adding duplicate keys, so just ignore it
        }
    }
}
