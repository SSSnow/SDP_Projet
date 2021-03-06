package ch.epfl.sdp.database.dao

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.epfl.sdp.database.data.Role
import ch.epfl.sdp.database.data.UserData
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class FirebaseUserDao : UserDao {

    companion object {
        private const val ROOT_PATH = "users"
    }

    private var database: FirebaseDatabase = Firebase.database

    private val groupOperators: MutableMap<String, MutableLiveData<Set<UserData>>> = mutableMapOf()
    private val groupRescuers: MutableMap<String, MutableLiveData<Set<UserData>>> = mutableMapOf()
    private val userIdGroups: MutableMap<String, MutableLiveData<Map<String, Role>>> = mutableMapOf()

    override fun getGroupIdsOfUserByEmail(email: String): LiveData<Map<String, Role>> {
        if (!userIdGroups.containsKey(email)) {
            //Initialise data
            val myRef = database.getReference(ROOT_PATH)
            userIdGroups[email] = MutableLiveData(mapOf())

            myRef.addChildEventListener(object : ChildEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Log.w("Firebase", "Failed to read value.")
                }

                override fun onChildMoved(dataSnapshot: DataSnapshot, p1: String?) {}
                override fun onChildChanged(dataSnapshot: DataSnapshot, p1: String?) {
                    val user = dataSnapshot.children.map {
                        it.getValue(UserData::class.java)!!
                    }.find { it.email == email }

                    if (user != null) {
                        userIdGroups[email]?.value = userIdGroups[email]?.value!!.plus(Pair(dataSnapshot.key!!, user.role))
                    } else {
                        userIdGroups[email]?.value = userIdGroups[email]?.value!!.minus(dataSnapshot.key!!)
                    }
                }

                override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
                    val user = dataSnapshot.children.map {
                        it.getValue(UserData::class.java)!!
                    }.find { it.email == email }

                    if (user != null) {
                        userIdGroups[email]?.value = userIdGroups[email]?.value!!.plus(Pair(dataSnapshot.key!!, user.role))
                    }
                }

                override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                    userIdGroups[email]?.value = userIdGroups[email]?.value?.minus(dataSnapshot.key!!)
                }
            })
        }
        return userIdGroups[email]!!
    }

    override fun getUsersOfGroupWithRole(groupId: String, role: Role): LiveData<Set<UserData>> {
        val mapData = if (role == Role.OPERATOR) groupOperators else groupRescuers

        if (!mapData.containsKey(groupId)) {
            //Initialise data
            val myRef = database.getReference("$ROOT_PATH/$groupId")
            mapData[groupId] = MutableLiveData(setOf())

            myRef.addChildEventListener(object : ChildEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Log.w("Firebase", "Failed to read value.")
                }

                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                    TODO("A user has changed, no action implemented")
                }

                override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
                    val user = dataSnapshot.getValue(UserData::class.java)!!
                    if (user.role == role) {
                        user.uuid = dataSnapshot.key!!
                        mapData[groupId]!!.value = mapData[groupId]!!.value!!.plus(user)
                    }
                }

                override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                    val user = dataSnapshot.getValue(UserData::class.java)!!
                    user.uuid = dataSnapshot.key!!
                    mapData[groupId]!!.value = mapData[groupId]!!.value!!.minus(user)
                }
            })
        }
        return mapData[groupId]!!
    }

    override fun removeUserFromSearchGroup(searchGroupId: String, userId: String) {
        database.getReference("$ROOT_PATH/$searchGroupId/$userId").removeValue()
    }

    override fun removeAllUserOfSearchGroup(searchGroupId: String) {
        database.getReference("$ROOT_PATH/${searchGroupId}").removeValue()
    }

    override fun addUserToSearchGroup(searchGroupId: String, user: UserData) {
        database.getReference("$ROOT_PATH/${searchGroupId}").push().setValue(user)
    }

}

