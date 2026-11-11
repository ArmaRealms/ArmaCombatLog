java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

repositories {
    maven("https://maven.elmakers.com/repository/")
}

dependencies {
    compileOnly("net.sacredlabyrinth.Phaed:PreciousStones:15.0")
}
