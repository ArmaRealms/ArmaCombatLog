java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    maven("https://nexus.sirblobman.xyz/jitpack-mirror/")
}

dependencies {
    compileOnly("com.github.mbax:VanishNoPacket:3.22")
}
