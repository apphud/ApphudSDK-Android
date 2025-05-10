package com.apphud.sdk.internal.data.local

import android.content.Context
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.dto.RuleScreenDto
import com.apphud.sdk.internal.data.mapper.RuleScreenMapper
import com.apphud.sdk.internal.domain.model.RuleScreen
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileReader
import java.io.FileWriter

internal class LocalRulesScreenRepository(
    private val context: Context,
    private val gson: Gson,
    private val ruleScreenMapper: RuleScreenMapper,
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
                        val dto = ruleScreenMapper.toDto(ruleScreen)
                        gson.toJson(dto, writer)
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
                        val dto = gson.fromJson(reader, RuleScreenDto::class.java)
                        val ruleScreen = ruleScreenMapper.toDomain(dto)
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
                        yield()
                        try {
                            FileReader(file).use { reader ->
                                val dto = gson.fromJson(reader, RuleScreenDto::class.java)
                                ruleScreenMapper.toDomain(dto)
                            }
                        } catch (e: Exception) {
                            ApphudLog.logE("$logPrefix Error reading rule screen file ${file.name}: ${e.message}")
                            null
                        }
                    }
                }
            }
        }
}
