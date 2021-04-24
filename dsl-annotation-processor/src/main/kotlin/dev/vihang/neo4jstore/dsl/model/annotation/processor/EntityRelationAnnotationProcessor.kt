package dev.vihang.neo4jstore.dsl.model.annotation.processor

import com.squareup.kotlinpoet.FileSpec
import dev.vihang.neo4jstore.dsl.model.annotation.Entity
import dev.vihang.neo4jstore.dsl.model.annotation.Relation
import dev.vihang.neo4jstore.dsl.model.annotation.Relations
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind.ERROR

@SupportedSourceVersion(javax.lang.model.SourceVersion.RELEASE_15)
@SupportedOptions(EntityRelationAnnotationProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
@SupportedAnnotationTypes(
    "dev.vihang.neo4jstore.dsl.model.annotation.Entity",
    "dev.vihang.neo4jstore.dsl.model.annotation.Relation",
    "dev.vihang.neo4jstore.dsl.model.annotation.Relations"
)
class EntityRelationAnnotationProcessor : AbstractProcessor() {

    override fun process(
        annotations: MutableSet<out TypeElement>?,
        roundEnv: RoundEnvironment?,
    ): Boolean {

        // list of entity class info
        val entityClassList = mutableListOf<ClassInfo>()

        // list of relation info
        val relationInfoList = mutableListOf<RelationInfo>()

        // list of all classes with @Entity annotation
        roundEnv?.getElementsAnnotatedWith(Entity::class.java)
            ?.forEach { element ->
                val className = element.simpleName.toString()
                val packageName = processingEnv.elementUtils.getPackageOf(element).toString()
                entityClassList.add(ClassInfo(className, packageName))
            }

        // set to check if the relation name is unique
        val uniqueRelationNameCheckSet = mutableSetOf<String>()

        roundEnv?.getElementsAnnotatedWith(Relation::class.java)
            ?.forEach { element ->
                // from class info
                val className = element.simpleName.toString()
                val packageName = processingEnv.elementUtils.getPackageOf(element).toString()

                val relation = element.getAnnotation(Relation::class.java)

                if (processRelation(
                        uniqueRelationNameCheckSet,
                        relation,
                        ClassInfo(className, packageName),
                        relationInfoList
                    )
                ) {
                    return true
                }
            }

        roundEnv?.getElementsAnnotatedWith(Relations::class.java)
            ?.forEach { element ->
                // from class info
                val className = element.simpleName.toString()
                val packageName = processingEnv.elementUtils.getPackageOf(element).toString()

                val relations = element.getAnnotation(Relations::class.java)

                relations.value.forEach { relation ->
                    if (processRelation(
                            uniqueRelationNameCheckSet,
                            relation,
                            ClassInfo(className, packageName),
                            relationInfoList
                        )
                    ) {
                        return true
                    }
                }
            }

        generateEntityContext(entityClassList, relationInfoList)
        return true
    }

    private fun processRelation(
        uniqueRelationNameCheckSet: MutableSet<String>,
        relation: Relation,
        from: ClassInfo,
        relationInfoList: MutableList<RelationInfo>
    ): Boolean {

        if (uniqueRelationNameCheckSet.contains(relation.name)) {
            processingEnv.messager.printMessage(ERROR, "Relation name ${relation.name} is not unique")
            return true
        }

        uniqueRelationNameCheckSet.add(relation.name)

        // to class info
        val relationInfo = RelationInfo(
            name = relation.name,
            from = from,
            to = ClassInfo(relation.to),
            forwardRelation = relation.forwardRelation,
            reverseRelation = relation.reverseRelation,
            forwardQuery = relation.forwardQuery,
            reverseQuery = relation.reverseQuery
        )
        relationInfoList.add(relationInfo)
        return false
    }

    private fun generateEntityContext(
        entityClassList: List<ClassInfo>,
        relationInfoList: MutableList<RelationInfo>
    ) {

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!

        entityClassList
            .groupBy { it.packageName }
            .forEach { (packageName: String, classInfoList: List<ClassInfo>) ->

                // file details
                val fileName = "EntityContexts"
                val fileSpec = FileSpec.builder(packageName, fileName)

                // generic imports
                fileSpec.addImport(DSL_MODEL_PACKAGE_NAME, "EntityContext")

                val fileContent = StringBuilder()

                classInfoList.forEach { (className, packageName) ->

                    fileSpec.addImport(packageName, className)

                    fileContent.append(
                        """
                        class ${className}Context(id: String) : EntityContext<$className>($className::class, id)
                        """.trimIndent()
                    )

                    fileContent.append("\n\n")

                    fileContent.append(
                        """
                        infix fun $className.Companion.withId(id: String) = ${className}Context(id)
                        """.trimIndent()
                    )

                    fileContent.append("\n\n")
                }

                // write to file

                fileSpec.build().writeTo(File(kaptKotlinGeneratedDir))

                val file = File("$kaptKotlinGeneratedDir/${packageName.replace('.', '/')}", "$fileName.kt")
                file.appendText(fileContent.toString())
            }

        relationInfoList
            .groupBy { it.from.packageName }
            .forEach { (packageName, relationInfos) ->

                // file details
                val fileName = "Relations"
                val fileSpec = FileSpec.builder(packageName, fileName)

                // generic imports
                fileSpec.addImport(SCHEMA_MODEL_PACKAGE_NAME, "Relation")
                fileSpec.addImport(SCHEMA_MODEL_PACKAGE_NAME, "None")
                fileSpec.addImport(SCHEMA_PACKAGE_NAME, "RelationType")
                fileSpec.addImport(DSL_MODEL_PACKAGE_NAME, "RelatedToClause")
                fileSpec.addImport(DSL_MODEL_PACKAGE_NAME, "RelatedFromClause")
                fileSpec.addImport(DSL_MODEL_PACKAGE_NAME, "RelationExpression")

                val fileContent = StringBuilder()

                relationInfos.forEach { relationInfo ->

                    val from = relationInfo.from.className
                    val to = relationInfo.to.className

                    fileSpec.addImport(relationInfo.from.packageName, from)
                    fileSpec.addImport(relationInfo.to.packageName, to)

                    val relationName = relationInfo.name.snakeToCamelCase()

                    val fromObj = from.decapitalize()
                    val toObj = to.decapitalize()

                    fileContent.append(
                        """
                        val ${relationName}Relation: Relation<$from, None, $to> = Relation(
                            name = "${relationInfo.name}",
                            from = $from::class,
                            relation = None::class,
                            to = $to::class,
                        )
                        
                        
                        val ${relationName}Type = RelationType(relation = ${relationName}Relation)
                        
                        
                        infix fun ${from}.Companion.${relationInfo.forwardQuery}(
                            $toObj: ${to}Context
                        ): RelatedToClause<$from, $to> = RelatedToClause(
                            relation = ${relationName}Relation,
                            toId = $toObj.id,
                        )
                        
                        
                        infix fun ${to}.Companion.${relationInfo.reverseQuery}(
                            $fromObj: ${from}Context
                        ): RelatedFromClause<$from, $to> = RelatedFromClause(
                            relation = ${relationName}Relation,
                            fromId = $fromObj.id
                        )
                        
                        infix fun ${from}Context.${relationInfo.forwardRelation}(
                            ${to.decapitalize()}: ${to}Context
                        ): RelationExpression<$from, None, $to> = RelationExpression(
                            relation = ${relationName}Relation,
                            fromId = id,
                            toId = ${to.decapitalize()}.id
                        )

                        infix fun ${to}Context.${relationInfo.reverseRelation}(
                            ${from.decapitalize()}: ${from}Context
                        ): RelationExpression<$from, None, $to> = RelationExpression(
                            relation = ${relationName}Relation,
                            fromId = ${from.decapitalize()}.id,
                            toId = id
                        )
                        
                        """.trimIndent()
                    )

                    fileContent.append("\n\n")
                }
                // write to file

                fileSpec.build().writeTo(File(kaptKotlinGeneratedDir))

                val file = File("$kaptKotlinGeneratedDir/${packageName.replace('.', '/')}", "$fileName.kt")
                file.appendText(fileContent.toString())
            }

    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val DSL_MODEL_PACKAGE_NAME = "dev.vihang.neo4jstore.dsl.model"
        const val SCHEMA_MODEL_PACKAGE_NAME = "dev.vihang.neo4jstore.schema.model"
        const val SCHEMA_PACKAGE_NAME = "dev.vihang.neo4jstore.schema"
    }
}
