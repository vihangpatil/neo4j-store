package dev.vihang.iam

import org.testcontainers.containers.DockerComposeContainer
import java.io.File

class KDockerComposeContainer(file: File) : DockerComposeContainer<KDockerComposeContainer>(file)