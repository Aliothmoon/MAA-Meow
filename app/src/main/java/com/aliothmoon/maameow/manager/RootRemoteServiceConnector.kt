package com.aliothmoon.maameow.manager

import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.RemoteServiceImpl

object RootRemoteServiceConnector : ProcessServiceConnectorBackend(SuSpawner) {

    override val backend = RemoteBackend.ROOT
    override val eventPrefix = "ROOT"
    override val processNameSuffix = "root_service"
    override val serviceClass: Class<*> = RemoteServiceImpl::class.java
    override val logFileName = "root_launch_debug.log"
    override val keepRoot: Boolean get() = keepRootForInputInjection
}
