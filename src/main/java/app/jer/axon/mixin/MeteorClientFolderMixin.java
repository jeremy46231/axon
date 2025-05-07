package app.jer.axon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.nio.file.Path;

@Mixin(MeteorClient.class)
public abstract class MeteorClientFolderMixin {

    // We are redirecting the call to `Path.toFile()` that is used to initialize MeteorClient.FOLDER
    // The original line is:
    // public static final File FOLDER = FabricLoader.getInstance().getGameDir().resolve(MOD_ID).toFile();
    // So, we target the `toFile()` method call on the Path object.
    @Redirect(method = "<clinit>", // <clinit> is the static initializer block
            at = @At(value = "INVOKE",
                    target = "Ljava/nio/file/Path;toFile()Ljava/io/File;"),
            remap = false // Important because Path.toFile() is a JRE method and not remapped by Yarn
    )
    private static File useCustomFolder(Path originalPathInstance) {
        // originalPathInstance will be FabricLoader.getInstance().getGameDir().resolve(MeteorClient.MOD_ID)

        Path expectedOriginalPath = FabricLoader.getInstance().getGameDir().resolve("meteor-client");
        if (!originalPathInstance.equals(expectedOriginalPath)) {
            // Not the call we intended to redirect, let it proceed normally
            return originalPathInstance.toFile();
        }

        Path customBasePath = FabricLoader.getInstance().getGameDir();
        File customFolder = customBasePath.resolve("axon").resolve("meteor").toFile();

        if (!customFolder.exists()) {
            if (!customFolder.mkdirs()) {
                throw new RuntimeException(
                        "Failed to create custom Meteor Client folder: " + customFolder.getAbsolutePath()
                );
            }
        }

        return customFolder;
    }
}