
package com.dan.perspective

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties

/**
Settings: all public var fields will be saved
 */
class Settings( private val activity: Activity) {

    companion object {
        const val DEFAULT_NAME = "output"
    }

    var jpegQuality = 95
    var hapticFeedback = true
    var prevLeftTopX = -1f
    var prevLeftTopY = -1f
    var prevRightTopX = -1f
    var prevRightTopY = -1f
    var prevRightBottomX = -1f
    var prevRightBottomY = -1f
    var prevLeftBottomX = -1f
    var prevLeftBottomY = -1f
    var prevWidth = 0
    var prevHeight = 0
    var autoDetectOnOpen = false
    var saveFolder: DocumentFile? = null

    init {
        loadProperties()
    }

    private fun forEachSettingProperty( listener: (KMutableProperty<*>)->Unit ) {
        for( member in this::class.declaredMemberProperties ) {
            if (member.visibility == KVisibility.PUBLIC && member is KMutableProperty<*>) {
                listener.invoke(member)
            }
        }
    }

    private fun loadProperties() {
        val preferences = activity.getPreferences(Context.MODE_PRIVATE)

        forEachSettingProperty { property ->
            try {
                when( property.returnType ) {
                    Boolean::class.createType() -> property.setter.call( this, preferences.getBoolean( property.name, property.getter.call(this) as Boolean ) )
                    Int::class.createType() -> property.setter.call( this, preferences.getInt( property.name, property.getter.call(this) as Int ) )
                    Long::class.createType() -> property.setter.call( this, preferences.getLong( property.name, property.getter.call(this) as Long ) )
                    Float::class.createType() -> property.setter.call( this, preferences.getFloat( property.name, property.getter.call(this) as Float ) )
                    String::class.createType() -> property.setter.call( this, preferences.getString( property.name, property.getter.call(this) as String ) )
                    DocumentFile::class.createType(nullable = true) -> {
                        val strUri = preferences.getString( property.name, "")
                        property.setter.call( this, null)
                        if (null != strUri && strUri.isNotEmpty()) {
                            property.setter.call( this, DocumentFile.fromTreeUri(activity, Uri.parse(strUri)) )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveProperties() {
        val preferences = activity.getPreferences(Context.MODE_PRIVATE)
        val editor = preferences.edit()

        forEachSettingProperty { property ->
            when( property.returnType ) {
                Boolean::class.createType() -> editor.putBoolean( property.name, property.getter.call(this) as Boolean )
                Int::class.createType() -> editor.putInt( property.name, property.getter.call(this) as Int )
                Long::class.createType() -> editor.putLong( property.name, property.getter.call(this) as Long )
                Float::class.createType() -> editor.putFloat( property.name, property.getter.call(this) as Float )
                String::class.createType() -> editor.putString( property.name, property.getter.call(this) as String )
                DocumentFile::class.createType(nullable = true) -> {
                    val value = property.getter.call(this) as DocumentFile?
                    val strValue = value?.uri?.toString() ?: ""
                    editor.putString( property.name, strValue )
                }
            }
        }

        editor.apply()
    }
}
