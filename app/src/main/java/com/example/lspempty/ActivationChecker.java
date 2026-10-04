package com.example.lspempty;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

/**
 * 是否对「游戏助手」生效：读 LSPosed 的 modules_config.db
 *（和 LSPosed Manager 勾选作用域是同一份表）。
 *
 * 已激活 = 本模块 enabled=1 且 scope 含 com.oplus.games。
 * 只勾选模块、不勾选游戏助手 → 未激活。
 */
final class ActivationChecker {

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
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Throwable ignored) {
                }
            }
            deleteLocalCopies(local);
        }
    }

    private static boolean moduleEnabledAndGamesInScope(SQLiteDatabase db) {
        try {
            int mid = -1;
            boolean enabled = false;
            try (Cursor c = db.rawQuery(
                    "SELECT mid, enabled FROM modules WHERE module_pkg_name=?",
                    new String[]{MODULE_PKG})) {
                if (c.moveToFirst()) {
                    mid = c.getInt(0);
                    enabled = c.getInt(1) == 1;
                }
            }
            if (!enabled || mid < 0) {
                return false;
            }
            try (Cursor c = db.rawQuery(
                    "SELECT app_pkg_name FROM scope WHERE mid=?",
                    new String[]{String.valueOf(mid)})) {
                while (c.moveToNext()) {
                    if (TARGET_PKG.equals(c.getString(0))) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            try (Cursor c = db.rawQuery(
                    "SELECT app_pkg_name FROM scope WHERE module_pkg_name=?",
                    new String[]{MODULE_PKG})) {
                while (c.moveToNext()) {
                    if (TARGET_PKG.equals(c.getString(0))) {
                        return true;
                    }
                }
            } catch (Throwable ignored2) {
                return false;
            }
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
