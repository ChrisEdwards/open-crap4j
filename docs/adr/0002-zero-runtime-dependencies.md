# Zero runtime dependencies in core and gradle-plugin

The core library and the Gradle plugin depend only on the JDK (and the Gradle API for the plugin). No picocli, no Jackson. A Gradle plugin's dependencies land on consumers' build classpaths where version conflicts are a chronic pain, and everything we need (XML parsing, JSON writing, argument parsing) is small enough to hand-roll. If the CLI ever wants a richer arg library, it gets shaded into the CLI jar only, never into core or the plugin.
