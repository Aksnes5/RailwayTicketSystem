package com.railway.ticketsystem.model

data class User(
    val id: String,
    val username: String,
    val password: String,
    val realName: String,
    val idCard: String,
    val phone: String,
    val email: String,
    val createTime: String,
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val avatarPath: String = "", // 头像路径
    val avatar: ByteArray? = null, // 头像数据
    val points: Int = 0 // 乘车积分
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (username != other.username) return false
        if (password != other.password) return false
        if (passwordHash != other.passwordHash) return false
        if (passwordSalt != other.passwordSalt) return false
        if (realName != other.realName) return false
        if (idCard != other.idCard) return false
        if (phone != other.phone) return false
        if (email != other.email) return false
        if (createTime != other.createTime) return false
        if (avatarPath != other.avatarPath) return false
        if (points != other.points) return false
        if (avatar != null) {
            if (other.avatar == null) return false
            if (!avatar.contentEquals(other.avatar)) return false
        } else if (other.avatar != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + (passwordHash?.hashCode() ?: 0)
        result = 31 * result + (passwordSalt?.hashCode() ?: 0)
        result = 31 * result + realName.hashCode()
        result = 31 * result + idCard.hashCode()
        result = 31 * result + phone.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + createTime.hashCode()
        result = 31 * result + avatarPath.hashCode()
        result = 31 * result + points
        result = 31 * result + (avatar?.contentHashCode() ?: 0)
        return result
    }
}




