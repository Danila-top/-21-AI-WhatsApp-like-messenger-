package com.danilatop.aimessenger.ai

import com.danilatop.aimessenger.data.AgentProfileEntity
import com.danilatop.aimessenger.data.AppDatabase

object AgentProfileCatalog {
    fun defaultProfiles(): List<AgentProfileEntity> =
        DefaultAgents.all.map { agent ->
            AgentProfileEntity(
                id = agent.id,
                name = agent.name,
                provider = agent.provider.name,
                model = agent.model,
                baseUrl = agent.baseUrl,
                keyName = agent.keyName,
                systemPrompt = agent.systemPrompt
            )
        }

    fun toSpec(profile: AgentProfileEntity): AgentSpec {
        return AgentSpec(
            id = profile.id,
            name = profile.name,
            provider = ProviderKind.valueOf(profile.provider),
            model = profile.model,
            baseUrl = profile.baseUrl,
            keyName = profile.keyName,
            systemPrompt = profile.systemPrompt
        )
    }

    suspend fun ensureDefaults(db: AppDatabase) {
        for (profile in defaultProfiles()) {
            if (db.agentProfiles().get(profile.id) == null) {
                db.agentProfiles().upsert(profile)
            }
        }
    }
}
