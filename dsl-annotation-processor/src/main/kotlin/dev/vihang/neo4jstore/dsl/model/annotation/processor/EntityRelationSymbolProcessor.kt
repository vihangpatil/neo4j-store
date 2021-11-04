package dev.vihang.neo4jstore.dsl.model.annotation.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import dev.vihang.neo4jstore.dsl.model.annotation.Relation
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion

class EntityRelationSymbolProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {

        // list of entity class info
        val entityClassList = resolver
            // Getting all symbols that are annotated with @Entity.
            .getSymbolsWithAnnotation("dev.vihang.neo4jstore.dsl.model.annotation.Entity")
            // Making sure we take only class declarations.
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .map {
                ClassInfo(it.simpleName.asString(), it.packageName.asString())
            }
            .toList()

        // list of relation info
        val relationInfoList = mutableListOf<RelationInfo>()

        // set to check if the relation name is unique
        val uniqueRelationNameCheckSet = mutableSetOf<String>()

        resolver
            // Getting all symbols that are annotated with @Relation.
            .getSymbolsWithAnnotation("dev.vihang.neo4jstore.dsl.model.annotation.Relation")
            // Making sure we take only class declarations.
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .forEach {
                // from class info
                val className = it.simpleName.asString()
                val packageName = it.packageName.asString()

                val relations = it.getAnnotationsByType(Relation::class)

                relations.forEach { relation ->
                    if (processRelation(
                            uniqueRelationNameCheckSet,
                            relation,
                            ClassInfo(className, packageName),
                            relationInfoList
                        )
                    ) {
                        return emptyList()
                    }
                }
            }

        generateEntityContext(entityClassList, relationInfoList)
        return emptyList()
    }

    private fun processRelation(
        uniqueRelationNameCheckSet: MutableSet<String>,
        relation: Relation,
        from: ClassInfo,
        relationInfoList: MutableList<RelationInfo>
    ): Boolean {

        if (uniqueRelationNameCheckSet.contains(relation.name)) {
            environment.logger.error("Relation name ${relation.name} is not unique")
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
                val fileWriter = environment.codeGenerator.createNewFile(
                    dependencies = Dependencies.ALL_FILES,
                    packageName = packageName,
                    fileName = fileName
                ).writer()
                fileSpec.build().writeTo(fileWriter)
                fileWriter.append(fileContent.toString())
                fileWriter.close()
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
                val fileWriter = environment.codeGenerator.createNewFile(
                    dependencies = Dependencies.ALL_FILES,
                    packageName = packageName,
                    fileName = fileName
                ).writer()
                fileSpec.build().writeTo(fileWriter)
                fileWriter.append(fileContent.toString())
                fileWriter.close()
            }

    }

    companion object {
        const val DSL_MODEL_PACKAGE_NAME = "dev.vihang.neo4jstore.dsl.model"
        const val SCHEMA_MODEL_PACKAGE_NAME = "dev.vihang.neo4jstore.schema.model"
        const val SCHEMA_PACKAGE_NAME = "dev.vihang.neo4jstore.schema"
    }
}

class EntityRelationSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor = EntityRelationSymbolProcessor(
        environment
    )
}