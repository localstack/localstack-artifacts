// copying this file here (unmodified) in order to make the "internal" variables below available to the patched code

package no.nav.common

import javax.security.auth.login.AppConfigurationEntry
import javax.security.auth.login.Configuration

/**
 *  An object for setting minimum JAAS context security for zookeeper and kafka broker
 *  - Server - for accessing zookeeper with given user&pwd
 *  - sasl_plaintext.KafkaServer - setting the user&pwd for kafka broker itself and users to access the broker
 *  - Client - user&pwd the kafka broker uses to access zookeeper
 *  - KafkaClient - user&pwd for e.g. schema registry
 *
 *  See code for zookeeper and kafka broker
 */

data class JAASCredential(val username: String, val password: String)

internal val kafkaAdmin = JAASCredential("srvkafkabroker", "kafkabroker")
internal val kafkaClient = JAASCredential("srvkafkaclient", "kafkaclient")
internal val kafkaC1 = JAASCredential("srvkafkac1", "kafkac1")
internal val kafkaP1 = JAASCredential("srvkafkap1", "kafkap1")
internal val kafkaC2 = JAASCredential("srvkafkac2", "kafkac2")
internal val kafkaP2 = JAASCredential("srvkafkap2", "kafkap2")

const val JAAS_PLAIN_LOGIN = "org.apache.kafka.common.security.plain.PlainLoginModule"
const val JAAS_DIGEST_LOGIN = "org.apache.zookeeper.server.auth.DigestLoginModule"
val JAAS_CF_REQUIRED: AppConfigurationEntry.LoginModuleControlFlag =
    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED
const val JAAS_REQUIRED = "required"

object JAASCustomUsers {

    private val users = mutableMapOf<String, String>()

    fun addUsers(u: List<JAASCredential>) {
        u.forEach { cred -> users["user_${cred.username}"] = cred.password }
    }

    fun getUsers(): Map<String, String> = users
}

fun setUpJAASContext() {

    val config = object : Configuration() {

        val kafkaServerUsersBase = mapOf(
            "username" to kafkaAdmin.username,
            "password" to kafkaAdmin.password,
            "user_${kafkaAdmin.username}" to kafkaAdmin.password,
            "user_${kafkaClient.username}" to kafkaClient.password,
            "user_${kafkaC1.username}" to kafkaC1.password,
            "user_${kafkaP1.username}" to kafkaP1.password,
            "user_${kafkaC2.username}" to kafkaC2.password,
            "user_${kafkaP2.username}" to kafkaP2.password
        ).plus(JAASCustomUsers.getUsers()).toList().toTypedArray()

        override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> =

            when (name) {
                // zookeeper section
                "Server" -> arrayOf(
                    AppConfigurationEntry(
                        JAAS_DIGEST_LOGIN,
                        JAAS_CF_REQUIRED,
                        hashMapOf<String, Any>(
                            "username" to kafkaAdmin.username,
                            "password" to kafkaAdmin.password,
                            "user_${kafkaAdmin.username}" to kafkaAdmin.password
                        )
                    )
                )
                // kafka server section
                "sasl_plaintext.KafkaServer" -> arrayOf(
                    AppConfigurationEntry(
                        JAAS_PLAIN_LOGIN,
                        JAAS_CF_REQUIRED,
                        hashMapOf<String, Any>(*kafkaServerUsersBase)
                    )
                )
                // kafka server as client of zookeeper
                "Client" -> arrayOf(
                    AppConfigurationEntry(
                        JAAS_DIGEST_LOGIN,
                        JAAS_CF_REQUIRED,
                        hashMapOf<String, Any>(
                            "username" to kafkaAdmin.username,
                            "password" to kafkaAdmin.password
                        )
                    )
                )
                // kafka client section, e.g. schema registry
                "KafkaClient" -> arrayOf(
                    AppConfigurationEntry(
                        JAAS_PLAIN_LOGIN,
                        JAAS_CF_REQUIRED,
                        hashMapOf<String, Any>(
                            "username" to kafkaClient.username,
                            "password" to kafkaClient.password
                        )
                    )
                )
                else -> {
                    println("JAAS section name -  $name")
                    arrayOf(
                        AppConfigurationEntry(
                            "invalid",
                            JAAS_CF_REQUIRED,
                            hashMapOf<String, Any>(
                                "username" to "invalid",
                                "password" to "invalid"
                            )
                        )
                    )
                }
            }

        override fun refresh() {
            // ignored
        }
    }
    // make the JAAS config available
    Configuration.setConfiguration(config)
}
