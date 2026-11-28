package com.aliothmoon.preferences.processor

import com.aliothmoon.preferences.PrefSchema
import com.google.devtools.ksp.ApiFeatures
import com.google.devtools.ksp.buildApiFeatures
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate

class PrefSchemaProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override val apiFeatures: ApiFeatures = buildApiFeatures {}

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(PrefSchema::class.qualifiedName!!)
        val unableToProcess = symbols.filterNot { it.validate(apiFeatures = apiFeatures) }.toList()

        symbols.filter { it is KSClassDeclaration && it.validate(apiFeatures = apiFeatures) }
            .forEach { symbol ->
                val classDecl = symbol as KSClassDeclaration
                processClass(classDecl)
            }

        return unableToProcess
    }

    private fun processClass(classDecl: KSClassDeclaration) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()

        val schemaAnnotation = classDecl.annotations
            .find { it.shortName.asString() == "PrefSchema" }
        val schemaName = schemaAnnotation?.arguments
            ?.find { it.name?.asString() == "name" }
            ?.value as? String ?: ""

        val properties = classDecl.getAllProperties()
            .filter { prop ->
                prop.annotations.any {
                    it.shortName.asString() == "PrefKey"
                }
            }
            .toList()

        if (properties.isEmpty()) {
            logger.warn("No @PrefKey  properties found in $className")
            return
        }

        val generator = SchemaCodeGenerator(
            codeGenerator = codeGenerator,
            packageName = packageName,
            className = className,
            schemaName = schemaName.ifEmpty { className },
            properties = properties,
            logger = logger,
            originatingFile = classDecl.containingFile!!
        )
        generator.generate()
    }
}
