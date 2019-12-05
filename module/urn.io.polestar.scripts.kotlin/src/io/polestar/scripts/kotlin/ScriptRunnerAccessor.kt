package io.polestar.scripts.kotlin

import org.netkernel.lang.kotlin.knkf.Identifier
import org.netkernel.lang.kotlin.knkf.context.SourceRequestContext
import org.netkernel.lang.kotlin.knkf.endpoints.KotlinAccessor

class ScriptRunnerAccessor: KotlinAccessor() {
    init {
        declareThreadSafe();
    }

    override fun SourceRequestContext.onSource() {
        responseFromRequest {
            sourceRequest("active:kotlinScript") {
                argument("operator", Identifier("arg:operator"))
                argument("name", Identifier("arg:name"))
                argument("state", Identifier("arg:state"))
                argumentByValue("scriptRuntimeSettings", kotlinScriptSettings)
            }
        }
    }
}
