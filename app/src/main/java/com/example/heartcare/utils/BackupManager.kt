package com.example.heartcare.utils

import android.content.Context
import com.example.heartcare.data.local.AppDatabase
import java.io.File

/** 数据库备份管理：复制 Room 数据库文件 + 清理超过保留天数的旧备份 */
object BackupManager {

    private const val DB_NAME = "heartcare.db"
    private const val PREFIX = "heartcare_backup_"

    fun backupDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "backup")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 生成当天备份文件（若已存在且 forceOverwrite=false 则跳过） */
    fun performBackup(context: Context, forceOverwrite: Boolean = true): File? {
        val day = DateUtils.formatYmd(System.currentTimeMillis())
        val dir = backupDir(context)
        val target = File(dir, "$PREFIX$day.db")
        if (target.exists() && !forceOverwrite) return target

        // Checkpoint WAL 后再复制，确保数据一致
        runCatching {
            val db = AppDatabase.getInstance(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        }

        val source = context.getDatabasePath(DB_NAME)
        if (!source.exists()) return null
        source.copyTo(target, overwrite = true)
        return target
    }

    /** 列出所有备份，按时间倒序 */
    fun listBackups(context: Context): List<File> {
        val dir = backupDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            ?: emptyList()
    }

    /** 保留最近 keepDays 个备份，其余删除 */
    fun cleanupOld(context: Context, keepDays: Int = 7) {
        val list = listBackups(context)
        if (list.size <= keepDays) return
        list.drop(keepDays).forEach { runCatching { it.delete() } }
    }
}
