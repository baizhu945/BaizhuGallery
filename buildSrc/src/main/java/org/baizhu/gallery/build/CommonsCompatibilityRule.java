package org.baizhu.gallery.build;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.attributes.Attribute;

public abstract class CommonsCompatibilityRule implements ComponentMetadataRule {
    public static final Attribute<String> ATTRIBUTE =
            Attribute.of("org.baizhu.gallery.commons-compatibility", String.class);
    public static final String ORIGINAL = "upstream";
    public static final String COMPATIBLE = "baizhu-7.1.3";

    @Override
    public void execute(ComponentMetadataContext context) {
        if (!"7.1.3".equals(context.getDetails().getId().getVersion())) {
            throw new IllegalStateException("Commons compatibility is pinned to 7.1.3; review the "
                    + "BaseSimpleActivity source and transform before changing the dependency version");
        }
        // Mark only Commons with a distinct artifact type, forcing AGP's requests for
        // aar (compile/runtime/lint) through our transform before its own AAR transforms.
        context.getDetails().allVariants(variant -> variant.attributes(attributes -> {
            attributes.attribute(org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    "baizhu-commons-aar");
            attributes.attribute(ATTRIBUTE, ORIGINAL);
        }));
    }
}
