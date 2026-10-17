package com.example.lspempty;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;

/**
 * 是否对「游戏助手」生效：读 LSPosed 的 modules_config.db
 *（和 LSPosed Manager 勾选作用域是同一份表）。
 *
 * 表结构（LSPosed 新版）：
 *   modules(module_pkg_name, apk_path) — 已安装模块列表，不含启用状态
 *   modules_state(module_pkg_name, user_id, enabled, scope_request_blocked) — 模块启用状态
 *   scope(module_pkg_name, app_pkg_name, user_id) — 作用域
 *
 * 已激活 = modules_state.enabled=1 且 scope 含 com.oplus.games。
 */
final class ActivationChecker {

    private static final String TAG = "GTBoost.Activation";

    static final String TARGET_PKG = "com.oplus.games";
    static final String MODULE_PKG = "com.example.lspempty";

    private static final String[] DB_CANDIDATES = {
            "/data/adb/lspd/config/modules_config.db",
            "/data/misc/lspd/config/modules_config.db"
    };

    private ActivationChecker() {}

    static boolean isGamesScopeEnabled(File cacheDir) {
        File local = new File(cacheDir, "gtboost_lspd.db");
        if (!copyLspDb(local)) {
            return false;
        }
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    local.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            return moduleEnabledAndGamesInScope(db);
        } catch (Throwable t) {
            Log.e(TAG, "open db failed: " + t);
            return false;
        } finally {
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
            deleteLocalCopies(local);
        }
    }

    private static boolean moduleEnabledAndGamesInScope(SQLiteDatabase db) {
        // 1) 查 modules_state 表：模块启用状态在这张表（modules 表只是已安装模块列表，不含启用状态）
        boolean enabled = false;
        try (Cursor c = db.rawQuery(
                "SELECT enabled FROM modules_state WHERE module_pkg_name=?",
                new String[]{MODULE_PKG})) {
            if (c.moveToFirst()) {
                enabled = c.getInt(0) == 1;
            }
        } catch (Throwable t) {
            Log.e(TAG, "modules_state query failed: " + t);
            return false;
        }
        if (!enabled) {
            return false;
        }

        // 2) 查 scope 表：确认 com.oplus.games 在作用域内
        try (Cursor c = db.rawQuery(
                "SELECT app_pkg_name FROM scope WHERE module_pkg_name=?",
                new String[]{MODULE_PKG})) {
            while (c.moveToNext()) {
                if (TARGET_PKG.equals(c.getString(0))) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "scope query failed: " + t);
            return false;
        }
        return false;
    }

    private static boolean copyLspDb(File dest) {
        String destPath = dest.getAbsolutePath();
        StringBuilder script = new StringBuilder();
        script.append("DST='").append(destPath).append("'; ");
        script.append("rm -f \"$DST\" \"$DST-wal\" \"$DST-shm\"; ");
        script.append("for DB in");
        for (String p : DB_CANDIDATES) {
            script.append(" ").append(p);
        }
        script.append("; do ");
        script.append("if [ -f \"$DB\" ]; then ");
        script.append("cp \"$DB\" \"$DST\"; ");
        script.append("[ -f \"$DB-wal\" ] && cp \"$DB-wal\" \"$DST-wal\"; ");
        script.append("[ -f \"$DB-shm\" ] && cp \"$DB-shm\" \"$DST-shm\"; ");
        script.append("chmod 666 \"$DST\" \"$DST-wal\" \"$DST-shm\" 2>/dev/null; ");
        script.append("exit 0; fi; done; exit 1");
        return runSu(script.toString()) == 0 && dest.isFile() && dest.length() > 0;
    }

    private static void deleteLocalCopies(File dest) {
        dest.delete();
        new File(dest.getAbsolutePath() + "-wal").delete();
        new File(dest.getAbsolutePath() + "-shm").delete();
    }

    private static int runSu(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return p.waitFor();
        } catch (Exception e) {
            return -1;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
