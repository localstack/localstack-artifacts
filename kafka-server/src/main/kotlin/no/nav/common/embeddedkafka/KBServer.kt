// Note: We need to copy this entire class to apply the patches for Listeners/AdvertisedListeners below...

package no.nav.common.embeddedkafka

import kafka.metrics.KafkaMetricsReporter
import kafka.server.KafkaConfig
import kafka.server.KafkaServer
import kafka.utils.VerifiableProperties
import no.nav.common.embeddedutils.ServerBase
import no.nav.common.embeddedutils.ServerStatus
import no.nav.common.kafkaAdmin
import no.nav.common.kafkaClient
import org.apache.kafka.common.utils.Time
import org.apache.kafka.streams.StreamsConfig
import scala.Option
import java.nio.file.Path
import java.util.Properties

class KBServer(
    override val port: Int,
    id: Int,
    noPartitions: Int,
    dataDir: Path,
    zkURL: String,
    withSecurity: Boolean,
    private val configOverrides: Properties
) : ServerBase() {

    // see link for KafkaServerStartable below for starting up an embedded kafka broker
    // https://github.com/apache/kafka/blob/2.2/core/src/main/scala/kafka/server/KafkaServerStartable.scala

    override val url = if (withSecurity) "SASL_PLAINTEXT://$host:$port" else "PLAINTEXT://$host:$port"

    private val kafkaProperties = getDefaultProps(
        id, zkURL, noPartitions, dataDir.resolve("log"),
        dataDir.resolve("logs"), dataDir.resolve("streams"), withSecurity
    )

    private val broker = KafkaServer(
        KafkaConfig(kafkaProperties),
        Time.SYSTEM,
        Option.apply(""),
        KafkaMetricsReporter.startReporters(VerifiableProperties(kafkaProperties))
    )

    override fun start() = when (status) {
        ServerStatus.NotRunning -> {
            broker.startup()
            status = ServerStatus.Running
        }
        else -> {
        }
    }

    override fun stop() = when (status) {
        ServerStatus.Running -> {
            broker.shutdown()
            broker.awaitShutdown()
            status = ServerStatus.NotRunning
        }
        else -> {
        }
    }

    private fun getDefaultProps(
        id: Int,
        zkURL: String,
        noPartitions: Int,
        logDir: Path,
        logDirs: Path,
        stateDir: Path,
        withSecurity: Boolean
    ) = Properties().apply {

        // see link below for details - trying to make lean embedded embeddedkafka broker
        // https://kafka.apache.org/documentation/#brokerconfigs

        if (withSecurity) {
            set("security.inter.broker.protocol", "SASL_PLAINTEXT")
            set("sasl.mechanism.inter.broker.protocol", "PLAIN")
            set("sasl.enabled.mechanisms", "PLAIN")
            set("authorizer.class.name", "kafka.security.auth.SimpleAclAuthorizer")
            set("super.users", "User:${kafkaAdmin.username};User:${kafkaClient.username}")
            // allow.everyone.if.no.acl.found=true
            // auto.create.topics.enable=false
            set("zookeeper.set.acl", "true")
        }
        set(KafkaConfig.AutoCreateTopicsEnableProp(), (!withSecurity).toString())

        set(KafkaConfig.ZkConnectProp(), zkURL)
        set(KafkaConfig.ZkConnectionTimeoutMsProp(), 10_000)
        set(KafkaConfig.ZkSessionTimeoutMsProp(), 30_000)

        set(KafkaConfig.BrokerIdProp(), id)
        // Note: code below has been changed to allow binding to 0.0.0.0
        set(KafkaConfig.ListenersProp(), url.replace("localhost", "0.0.0.0"))
        set(KafkaConfig.AdvertisedListenersProp(), url)

        set(KafkaConfig.NumNetworkThreadsProp(), 3) // 3
        set(KafkaConfig.NumIoThreadsProp(), 8) // 8
        set(KafkaConfig.BackgroundThreadsProp(), 10) // 10

        // noPartitions is identical with no of brokers
        set(KafkaConfig.NumPartitionsProp(), noPartitions)
        set(KafkaConfig.DefaultReplicationFactorProp(), noPartitions)
        set(KafkaConfig.MinInSyncReplicasProp(), 1)

        set(KafkaConfig.OffsetsTopicPartitionsProp(), noPartitions) // 50
        set(KafkaConfig.OffsetsTopicReplicationFactorProp(), noPartitions.toShort()) // 3

        set(KafkaConfig.TransactionsTopicPartitionsProp(), noPartitions) // 50
        set(KafkaConfig.TransactionsTopicReplicationFactorProp(), noPartitions.toShort()) // 3
        set(KafkaConfig.TransactionsTopicMinISRProp(), noPartitions)

        set(KafkaConfig.LeaderImbalanceCheckIntervalSecondsProp(), 10)
        set(KafkaConfig.LogDirProp(), logDir.toAbsolutePath().toString())
        set(KafkaConfig.LogDirsProp(), logDirs.toAbsolutePath().toString())
        set(StreamsConfig.STATE_DIR_CONFIG, stateDir.toAbsolutePath().toString())

        set(KafkaConfig.NumRecoveryThreadsPerDataDirProp(), 1)

        set(KafkaConfig.ControlledShutdownMaxRetriesProp(), 1)
        set(KafkaConfig.ControlledShutdownRetryBackoffMsProp(), 500)

        putAll(configOverrides)
    }
}
