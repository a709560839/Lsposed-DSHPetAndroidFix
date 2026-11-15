package com.jj.dshpet.lsposed.provider;

import static com.jj.dshpet.lsposed.constant.Constants.PROVIDER_EXTRA_DEFAULT;
import static com.jj.dshpet.lsposed.constant.Constants.PROVIDER_EXTRA_VALUE;
import static com.jj.dshpet.lsposed.constant.Constants.PROVIDER_METHOD_GET_BOOLEAN;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jj.dshpet.lsposed.config.ConfigManager;

/**
 * 跨进程配置 ContentProvider
 * <p>
 * 供被 Hook 的宿主进程（io.dsh.pet.ai）实时跨进程调用 call() 读取模块配置。
 */
public class ConfigProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (PROVIDER_METHOD_GET_BOOLEAN.equals(method) && arg != null && getContext() != null) {
            boolean def = extras != null && extras.getBoolean(PROVIDER_EXTRA_DEFAULT, true);
            boolean val = ConfigManager.getPrefs(getContext()).getBoolean(arg, def);
            Bundle response = new Bundle();
            response.putBoolean(PROVIDER_EXTRA_VALUE, val);
            return response;
        }
        return super.call(method, arg, extras);
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
