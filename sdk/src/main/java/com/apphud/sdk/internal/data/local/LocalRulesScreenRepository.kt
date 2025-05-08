package com.apphud.sdk.internal.data.local

import android.content.Context
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.domain.model.Rule
import com.apphud.sdk.internal.domain.model.RuleScreen
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

internal class LocalRulesScreenRepository(
    private val context: Context,
    private val gson: Gson,
) {
    private val logPrefix = "[RulesScreenRepo]"

    private val rulesDir: File
        get() {
            val apphudDir = File(context.filesDir, "apphud")
            return File(apphudDir, "rules_screen").also {
                if (!it.exists()) {
                    ApphudLog.log("$logPrefix Creating rules_screen directory")
                    it.mkdirs()
                }
            }
        }

    private val fileMutex = Mutex()

    suspend fun save(ruleScreen: RuleScreen): Result<Unit> =
        runCatchingCancellable {
            ApphudLog.log("$logPrefix Saving rule screen with id: ${ruleScreen.rule.id}")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    val ruleFile = File(rulesDir, "${ruleScreen.rule.id}.json")
                    FileWriter(ruleFile).use { writer ->
                        gson.toJson(ruleScreen, writer)
                    }
                    ApphudLog.log("$logPrefix Rule screen saved successfully: ${ruleScreen.rule.id}")
                }
            }
        }

    suspend fun getById(ruleId: String): Result<RuleScreen?> =
        runCatchingCancellable {
            ApphudLog.log("$logPrefix Getting rule screen by id: $ruleId")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    val ruleFile = File(rulesDir, "$ruleId.json")
                    if (!ruleFile.exists()) {
                        ApphudLog.log("$logPrefix Rule screen file not found for id: $ruleId")
                        return@withLock null
                    }

                    FileReader(ruleFile).use { reader ->
                        val ruleScreen = gson.fromJson(reader, RuleScreen::class.java)
                        ApphudLog.log("$logPrefix Rule screen loaded successfully: $ruleId")
                        ruleScreen
                    }
                }
            }
        }

    suspend fun getAll(): Result<List<RuleScreen>> =
        runCatchingCancellable {
            ApphudLog.log("$logPrefix Getting all rule screens")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    val files = rulesDir.listFiles()
                        ?.filter { it.isFile && it.extension == "json" }
                        ?: emptyList()

                    ApphudLog.log("$logPrefix Found ${files.size} rule screen files")

                    files.mapNotNull { file ->
                        try {
                            FileReader(file).use { reader ->
                                gson.fromJson(reader, RuleScreen::class.java)
                            }
                        } catch (e: Exception) {
                            ApphudLog.logE("$logPrefix Error reading rule screen file ${file.name}: ${e.message}")
                            null
                        }
                    }
                }
            }
        }

    suspend fun deleteById(ruleId: String): Result<Boolean> =
        runCatchingCancellable {
            ApphudLog.log("$logPrefix Deleting rule screen by id: $ruleId")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    val ruleFile = File(rulesDir, "$ruleId.json")
                    if (ruleFile.exists()) {
                        val result = ruleFile.delete()
                        ApphudLog.log("$logPrefix Rule screen deletion result for $ruleId: $result")
                        result
                    } else {
                        ApphudLog.log("$logPrefix Rule screen file not found for deletion: $ruleId")
                        false
                    }
                }
            }
        }

    suspend fun deleteAll(): Result<Boolean> =
        runCatchingCancellable {
            ApphudLog.log("$logPrefix Deleting all rule screens")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    val files = rulesDir.listFiles()
                        ?.filter { it.isFile && it.extension == "json" }
                        ?: emptyList()

                    ApphudLog.log("$logPrefix Found ${files.size} rule screen files to delete")

                    val result = files.all { it.delete() }
                    ApphudLog.log("$logPrefix All rule screens deletion result: $result")
                    result
                }
            }
        }

    suspend fun getRules(): Result<List<Rule>> =
        runCatchingCancellable {
            ApphudLog.log("$logPrefix Getting all rules")
            withContext(Dispatchers.IO) {
                fileMutex.withLock {
                    val rules = getAll().getOrNull()?.map { it.rule } ?: emptyList()
                    ApphudLog.log("$logPrefix Retrieved ${rules.size} rules")
                    rules
                }
            }
        }
}
