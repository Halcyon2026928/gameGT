package com.example.lspempty;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 跨进程配置提供器。
 * LSPosed hook 运行在 com.oplus.games 进程，无法直接读取本模块的 SharedPreferences，
 * 通过 ContentResolver.call() 读写配置。
 */
public class SettingsProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.lspempty.settings";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String PREFS_NAME = "module_settings";
    public static final String KEY_BLOCK_STARTUP = "block_startup";
    public static final String KEY_REMOVE_LIMIT = "remove_limit";
    public static final String KEY_HIDE_RECOMMEND = "hide_recommend";
    public static final String KEY_ACTIVATED = "activated";

    public static final String METHOD_GET_BOOLEAN = "getBoolean";
    public static final String METHOD_SET_BOOLEAN = "setBoolean";
    public static final String EXTRA_VALUE = "value";

    private SharedPreferences prefs;

    @Override
    public boolean onCreate() {
        prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Bundle result = new Bundle();
        if (METHOD_GET_BOOLEAN.equals(method)) {
            boolean def = extras != null && extras.getBoolean("def", true);
            result.putBoolean(EXTRA_VALUE, prefs.getBoolean(arg, def));
        } else if (METHOD_SET_BOOLEAN.equals(method)) {
            boolean val = extras != null && extras.getBoolean(EXTRA_VALUE, false);
            prefs.edit().putBoolean(arg, val).commit();
            result.putBoolean(EXTRA_VALUE, val);
        }
        return result;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
