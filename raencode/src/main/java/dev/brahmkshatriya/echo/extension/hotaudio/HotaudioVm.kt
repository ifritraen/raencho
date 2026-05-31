package dev.brahmkshatriya.echo.extension.hotaudio

class HotaudioVm {

    companion object {
        private val BYTECODE = intArrayOf(
            34,35,35,34,36,35,123,0,124,58,35,34,34,36,113,35,36,34,120,0,35,34,37,35,123,0,124,68,125,97,125,116,125,101,35,34,34,37,119,35,38,34,35,38,127,35,38,34,35,38,91,232,2,1,91,3,97,8,85,2,2,1,1,35,96,2,3,1,91,0,2,1,1,3,85,2,4,1,2,4,122,2,35,34,38,35,34,36,113,38,36,34,114,36,2,1,4,2,91,0,2,1,3,2,91,0,2,1,5,2,91,0,2,1,6,2,91,35,2,1,91,170,97,8,85,2,2,1,91,191,97,16,85,2,2,1,91,46,97,24,85,2,2,1,7,2,1,4,81,3,2,1,8,2,95,8,99,102,34,36,115,3,2,1,9,2,1,5,80,9,2,1,5,2,1,5,82,7,2,1,5,2,91,11,2,1,1,5,87,2,9,1,2,9,1,5,83,2,9,1,5,9,1,6,83,5,9,1,6,9,1,6,82,7,9,1,6,9,91,11,9,1,1,6,87,9,2,1,9,2,1,6,83,9,2,1,6,2,1,5,83,6,2,1,5,2,89,3,1,4,81,3,2,1,8,2,95,8,100,102,123,0,36,34,35,36,123,0,124,57,36,34,34,35,113,36,35,34,123,0,124,58,36,34,34,35,113,36,35,34,34,35,113,38,36,34,35,36,122,5,36,34,38,36,34,35,113,38,35,34,122,6,38,34,36,38,34,35,113,36,35,34,126,0,36,34,38,36,91,62,5,1,91,11,97,8,85,5,5,1,34,38,118,5,2,1,5,2,91,0,2,1,1,5,88,2,8,1,5,8,8,5,91,120,5,1,91,82,97,8,85,5,5,1,34,38,118,5,2,1,5,2,91,1,2,1,1,5,88,2,7,1,5,7,1,5,85,8,7,1,8,7,91,45,7,1,91,129,97,8,85,7,7,1,34,38,118,7,5,1,7,5,91,2,5,1,1,7,88,5,2,1,7,2,1,7,85,8,2,1,8,2,91,41,2,1,91,116,97,8,85,2,2,1,34,38,118,2,7,1,2,7,91,3,7,1,1,2,88,7,5,1,2,5,1,2,85,8,5,1,8,5,91,5,5,1,91,28,97,8,85,5,5,1,34,38,118,5,2,1,5,2,91,4,2,1,1,5,88,2,7,1,5,7,1,5,85,8,7,1,8,7,123,0,124,95,125,95,125,104,125,97,125,95,125,99,125,104,125,117,125,110,125,107,125,115,38,34,34,37,118,38,7,1,95,7,99,26,91,1,7,1,5,7,91,7,7,1,1,5,88,7,2,1,5,2,1,5,85,8,2,1,8,2,123,0,124,115,125,116,125,97,125,99,125,107,38,34,36,38,34,37,119,36,38,34,39,38,34,39,119,36,38,34,39,38,34,39,119,36,38,34,39,38,123,0,124,10,38,34,36,38,34,39,128,36,38,34,36,38,114,36,2,1,5,2,91,3,2,1,7,2,1,5,96,7,2,1,7,2,95,7,99,34,91,1,7,1,2,7,91,5,7,1,1,2,88,7,5,1,2,5,1,2,85,8,5,1,8,5,91,0,5,1,95,5,99,0,123,0,124,77,125,101,125,100,125,105,125,97,125,83,125,111,125,117,125,114,125,99,125,101,36,34,38,36,123,0,124,91,125,110,125,97,125,116,125,105,125,118,125,101,36,34,39,36,123,0,124,117,125,110,125,100,125,101,125,102,125,105,125,110,125,101,125,100,36,34,40,36,36,37,34,36,119,38,41,34,36,41,129,36,41,34,36,41,34,36,131,40,5,1,2,5,95,2,99,8,91,0,2,1,95,2,99,270,34,36,130,39,2,1,5,2,95,5,99,8,91,0,5,1,95,5,99,26,91,1,5,1,2,5,91,6,5,1,1,2,88,5,7,1,2,7,1,2,85,8,7,1,8,7,123,0,124,83,125,111,125,117,125,114,125,99,125,101,125,66,125,117,125,102,125,102,125,101,125,114,36,34,40,36,36,37,34,36,119,40,41,34,36,41,129,36,41,34,36,41,34,36,130,39,7,1,2,7,95,2,99,8,91,0,2,1,95,2,99,26,91,1,2,1,7,2,91,6,2,1,1,7,88,2,5,1,7,5,1,7,85,8,5,1,8,5,36,37,34,36,119,40,37,34,36,37,123,0,124,112,125,114,125,111,125,116,125,111,125,116,125,121,125,112,125,101,37,34,40,37,34,36,119,40,37,34,40,37,123,0,124,97,125,112,125,112,125,101,125,110,125,100,125,66,125,117,125,102,125,102,125,101,125,114,37,34,36,37,34,40,119,36,37,34,36,37,129,36,37,34,36,37,34,36,130,39,5,1,7,5,95,7,99,8,91,0,7,1,95,7,99,26,91,1,7,1,5,7,91,6,7,1,1,5,88,7,2,1,5,2,1,5,85,8,2,1,8,2,91,156,2,1,91,39,97,8,85,2,2,1,91,91,97,16,85,2,2,1,91,70,97,24,85,2,2,1,5,2,91,4,2,1,1,6,87,2,7,1,2,7,1,2,83,8,7,1,8,7,91,1,7,1,2,7,1,2,84,8,7,1,6,7,94,6,7,1,6,7,1,6,84,5,7,1,6,7,91,1,7,1,1,8,87,7,3,1,7,3,1,7,83,6,3,1,8,3,1,2,84,8,3,1,7,3,94,7,3,1,7,3,1,7,84,5,3,1,7,3,91,1,3,1,1,8,87,3,6,1,3,6,1,3,83,7,6,1,8,6,1,2,84,8,6,1,3,6,94,3,6,1,3,6,1,3,84,5,6,1,3,6,91,1,6,1,1,8,87,6,7,1,6,7,1,6,83,3,7,1,8,7,1,2,84,8,7,1,2,7,94,2,7,1,2,7,1,2,84,5,7,1,2,7,91,1,7,1,1,8,87,7,5,1,7,5,1,7,83,2,5,1,8,5,122,8,36,34,39,36,34,35,113,39,35,34,34,35
        )
    }

    fun sign(payload: String): String {
        val d = Array<Any>(80) { 0 }
        
        for (i in 34 until 80) {
            d[i] = ""
        }
        d[35] = payload

        fun getInt(reg: Int): Int {
            return when (val v = d[reg]) {
                is Number -> v.toInt()
                is Boolean -> if (v) 1 else 0
                is String -> v.toIntOrNull() ?: 0
                else -> 0
            }
        }

        fun getLong(reg: Int): Long {
            return when (val v = d[reg]) {
                is Number -> v.toLong()
                is Boolean -> if (v) 1L else 0L
                is String -> v.toLongOrNull() ?: 0L
                else -> 0L
            }
        }

        fun getString(reg: Int): String {
            return d[reg].toString()
        }

        var steps = 0
        while (steps < 100000) {
            val ip = getInt(0)
            if (ip >= BYTECODE.size) break
            
            val opcode = BYTECODE[ip]
            d[0] = ip + 1
            val opIp = getInt(0)
            if (opIp >= BYTECODE.size) break
            val operand = BYTECODE[opIp]
            d[0] = opIp + 1
            
            steps++

            try {
                if (opcode < 80) {
                    d[opcode] = d[operand]
                } else {
                    when (opcode) {
                        80 -> d[1] = ((getLong(1) + getLong(operand)) and 0xFFFFFFFFL).toInt()
                        81 -> d[1] = ((getLong(1) - getLong(operand)) and 0xFFFFFFFFL).toInt()
                        82 -> d[1] = getInt(1) * getInt(operand)
                        83 -> d[1] = getInt(1) xor getInt(operand)
                        84 -> d[1] = getInt(1) and getInt(operand)
                        85 -> d[1] = getInt(1) or getInt(operand)
                        86 -> d[1] = getInt(1) ushr getInt(operand)
                        87 -> d[1] = getInt(1) ushr getInt(operand)
                        88 -> d[1] = getInt(1) shl getInt(operand)
                        89 -> d[operand] = getInt(operand) + 1
                        90 -> d[operand] = getInt(operand) - 1
                        91 -> d[1] = operand
                        92 -> {
                            val amt = getInt(operand)
                            val v = getInt(1)
                            d[1] = (v ushr amt) or (v shl (32 - amt))
                        }
                        93 -> {
                            val amt = getInt(operand)
                            val v = getInt(1)
                            d[1] = (v shl amt) or (v ushr (32 - amt))
                        }
                        94 -> d[1] = -getInt(operand)
                        95 -> d[1] = getInt(operand)
                        96 -> d[1] = ((getInt(1).toLong() and 0xFFFFFFFFL) / (getInt(operand).toLong() and 0xFFFFFFFFL)).toInt()
                        97 -> d[1] = getInt(1) shl operand
                        99 -> if (getInt(1) == 0) d[0] = getInt(0) + operand
                        100 -> if (getInt(1) != 0) d[0] = getInt(0) - operand
                        113 -> d[34] = getString(34) + getString(operand)
                        114 -> {
                            val obj = d[operand]
                            d[1] = when (obj) {
                                is List<*> -> obj.size
                                is Map<*, *> -> obj.size
                                is String -> obj.length
                                else -> 0
                            }
                        }
                        115 -> d[1] = getString(34).getOrNull(getInt(operand))?.code ?: 0
                        118 -> {
                            val key = getString(operand)
                            val obj = d[34]
                            d[1] = if (obj is Map<*, *>) {
                                if (obj.containsKey(key)) 1 else 0
                            } else if (obj is List<*>) {
                                val idx = key.toIntOrNull()
                                if (idx != null && idx in obj.indices) 1 else 0
                            } else {
                                0
                            }
                        }
                        119 -> {
                            val key = getString(operand)
                            val obj = d[34]
                            if (obj == null || obj == "undefined" || obj == "") {
                                throw NullPointerException("Cannot read properties of undefined (reading '$key')")
                            }
                            d[34] = when (obj) {
                                is Map<*, *> -> {
                                    val res = obj[key]
                                    if (res == null && obj.containsKey("myQ")) {
                                        if (key == "Date") "Date"
                                        else if (key == "Error") "Error"
                                        else "undefined"
                                    } else {
                                        res ?: "undefined"
                                    }
                                }
                                is List<*> -> {
                                    val idx = key.toIntOrNull()
                                    if (idx != null && idx in obj.indices) obj[idx] ?: "undefined" else "undefined"
                                }
                                is String -> {
                                    val idx = key.toIntOrNull()
                                    if (idx != null && idx in obj.indices) obj[idx].toString() else "undefined"
                                }
                                is JsException -> {
                                    if (key == "stack") obj.stack else "undefined"
                                }
                                else -> "undefined"
                            }
                        }
                        120 -> {
                            d[34] = mapOf(
                                "myQ" to listOf(
                                    "0.fuT*}D", ";0v]+r=D", "..RPs", ">eYK", "V=!S]knD", "length", 3, 206, "undefined", 2, 0, 1, 5, 100, 107, 63, 6, "fromCodePoint", 12, "push", "a", null, 23, 18, 15, "call", 4, 138, "b", 45, 43, 150, "l", "h", "apply", 27, "o", "e", "j", "f", "g", "c", "i", 88, 255, 8, 7, 14, 151, 75, 177, 148, 28, 147, 146, 169, 159, 16, 209, 253, 91, 8191, 13, "d", 248, 61, 90, 92, 171, 31, 30, 29, 199, "k", 131, 185, 124, 154, 9, 52, 11, 119, 143, 112, 46, 40, 20, 229, 221, 136, 211, 19, "nt", 26, "m", 36, 39, 149, 175, 34, 80, 35, 47, 82, 83, 94, 32, 93, 135, 186, 96, 98, 196, 205, 115, 44, 987, 50, 51, 118, 704, 84, 33, "ng", 53, 55, "F", 54, 225, 117, 97, 101, 232, 224, 130, 59, 77, 70, 99, 174, 58, false, 114, 121, 126, 219, 48, 49, 233, 127, 128, 64, 66, "es", 204, 67, 95, 60, 123, 87, 164, 74, 81, 73, 17, 443, 21, 22, 867, 706, 468, 79, 595, 111, 109, 86, 223, 89, 57, "on", "pe", 239, 227, 789, 38, 105, 106, "or", 104, 116, 230, 120, "n", "te", 122, 222, 125, 71, 129, 158, 10, 242, 139
                                )
                            )
                        }
                        122 -> {
                            val v = getLong(operand) and 0xFFFFFFFFL
                            d[34] = v.toString(16).padStart(8, '0')
                        }
                        123 -> d[34] = ""
                        124 -> d[34] = operand.toChar().toString()
                        125 -> d[34] = getString(34) + operand.toChar().toString()
                        126 -> {
                            d[34] = mutableMapOf<String, Any>()
                        }
                        127 -> {
                            val ctor = d[operand]
                            d[34] = when (ctor) {
                                "Date" -> mapOf<String, Any>()
                                "Error" -> {
                                    val stack = """
                                        Error
                                            at evalmachine.<anonymous>:1:62650
                                            at Proxy.S (evalmachine.<anonymous>:1:37987)
                                            at main (D:\C\p6\echo-extension\trace_execution.js:61:18)
                                            at Object.<anonymous> (D:\C\p6\echo-extension\trace_execution.js:65:1)
                                            at Module._compile (node:internal/modules/cjs/loader:1830:14)
                                            at Object..js (node:internal/modules/cjs/loader:1961:10)
                                            at Module.load (node:internal/modules/cjs/loader:1553:32)
                                            at Module._load (node:internal/modules/cjs/loader:1355:12)
                                            at wrapModuleLoad (node:internal/modules/cjs/loader:255:19)
                                            at Module.executeUserEntryPoint [as runMain] (node:internal/modules/run_main:154:5)
                                    """.trimIndent()
                                    JsException("Error", stack)
                                }
                                else -> throw Exception("$ctor is not a constructor")
                            }
                        }
                        128 -> {
                            val str = getString(34)
                            val delimiter = getString(operand)
                            d[34] = str.split(delimiter)
                        }
                        129 -> {
                            d[34] = getString(operand)
                        }
                        130 -> {
                            val container = getString(34)
                            val search = getString(operand)
                            d[1] = if (container.contains(search)) 1 else 0
                        }
                        131 -> {
                            val a = getString(34)
                            val b = getString(operand)
                            d[1] = if (a == b) 1 else 0
                        }
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: "TypeError"
                val stack = """
                    TypeError: $msg
                        at evalmachine.<anonymous>:1:62650
                        at Proxy.S (evalmachine.<anonymous>:1:37987)
                        at main (D:\C\p6\echo-extension\trace_execution.js:61:18)
                        at Object.<anonymous> (D:\C\p6\echo-extension\trace_execution.js:65:1)
                        at Module._compile (node:internal/modules/cjs/loader:1830:14)
                        at Object..js (node:internal/modules/cjs/loader:1961:10)
                        at Module.load (node:internal/modules/cjs/loader:1553:32)
                        at Module._load (node:internal/modules/cjs/loader:1355:12)
                        at wrapModuleLoad (node:internal/modules/cjs/loader:255:19)
                        at Module.executeUserEntryPoint [as runMain] (node:internal/modules/run_main:154:5)
                """.trimIndent()
                d[34] = JsException(msg, stack)
            }

        }

        return getString(34)
    }
}

class JsException(val message: String, val stack: String) {
    override fun toString(): String {
        return "JsException(message=$message, stack=${stack.take(50)}...)"
    }
}
