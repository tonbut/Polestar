package io.polestar.scripts.kotlin

import org.netkernel.lang.kotlin.knkf.context.*
import org.netkernel.lang.kotlin.script.*
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*

val kotlinScriptSettings = NetKernelScriptRuntimeSettings(PolestarScriptRepresentation::class, PolestarScriptConfiguration) {
    ScriptEvaluationConfiguration {
        check(it is SourceRequestContext)

        providedProperties(Pair("context", it as SourceRequestContext))
    }
}

fun getPolestarKotlinScriptSettings() = NetKernelScriptRuntimeSettings(PolestarScriptRepresentation::class, PolestarScriptConfiguration) {
    ScriptEvaluationConfiguration {
        check(it is SourceRequestContext)

        providedProperties(Pair("context", it as SourceRequestContext))
    }
}

object PolestarScriptConfiguration : ScriptCompilationConfiguration({
    defaultImports("org.netkernel.layer0.nkf.INKFRequestContext", "org.netkernel.lang.kotlin.knkf.context.*", "org.netkernel.lang.kotlin.knkf.*")
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
    baseClass(PolestarKotlinScript::class)
})

@KotlinScript(fileExtension = "ps.kts", displayName = "Polestar Kotlin Script", compilationConfiguration = PolestarScriptConfiguration::class)
abstract class PolestarKotlinScript(val context: SourceRequestContext) {

}

class PolestarScriptRepresentation(script: CompiledScript<*>): BaseScriptRepresentation(script)

class PolestarScriptTransreptor: BaseKotlinScriptTransreptor<PolestarScriptRepresentation>() {
    init {
        this.toRepresentation(PolestarScriptRepresentation::class.java)
    }

    override fun TransreptorRequestContext<PolestarScriptRepresentation>.onTransrept() {
        response {
            PolestarScriptRepresentation(performCompilation(PolestarScriptConfiguration))
        }
    }
}
