package de.mossgrabers.framework.scale;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Persistence helper for custom scales.
 */
public class CustomScaleLibrary
{
    private final Path          file;
    private final ObjectMapper  mapper;
    private volatile String     lastError;


    public CustomScaleLibrary (final Path file)
    {
        this.file = file;
        this.mapper = new ObjectMapper ();
        this.mapper.enable (SerializationFeature.INDENT_OUTPUT);
    }


    public static Path getDefaultFile ()
    {
        final Path baseDir = getDefaultBaseDirectory ();
        return baseDir.resolve ("custom-scales.json");
    }


    public String getLastError ()
    {
        return this.lastError;
    }


    public List<CustomScale> loadAll ()
    {
        this.lastError = null;

        if (!Files.isRegularFile (this.file))
            return Collections.emptyList ();

        try (InputStream in = Files.newInputStream (this.file))
        {
            final CustomScale [] all = this.mapper.readValue (in, CustomScale[].class);

            final List<String> validationErrors = new ArrayList<> ();
            final Set<String> names = new HashSet<> ();
            for (final CustomScale scale: all)
            {
                if (scale == null)
                    continue;
                final String name = scale.getName ();
                if (name == null || name.isBlank ())
                    validationErrors.add ("Name must not be empty.");
                else if (!names.add (name))
                    validationErrors.add ("Duplicate scale name: " + name);

                final List<String> errors = scale.validate ();
                if (!errors.isEmpty ())
                    validationErrors.addAll (errors);
            }

            if (!validationErrors.isEmpty ())
            {
                this.lastError = String.join ("; ", validationErrors);
                return Collections.emptyList ();
            }

            final List<CustomScale> result = new ArrayList<> (all.length);
            Collections.addAll (result, all);
            return result;
        }
        catch (final IOException ex)
        {
            this.lastError = ex.getMessage ();
            return Collections.emptyList ();
        }
    }


    public void saveAll (final List<CustomScale> scales) throws IOException
    {
        this.lastError = null;

        if (scales == null)
            throw new IllegalArgumentException ("scales must not be null");

        Files.createDirectories (this.file.getParent ());

        final Path tmp = this.file.resolveSibling (this.file.getFileName ().toString () + ".tmp");
        try (OutputStream out = Files.newOutputStream (tmp))
        {
            this.mapper.writeValue (out, scales);
        }

        Files.move (tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
    }


    private static Path getDefaultBaseDirectory ()
    {
        final String osName = System.getProperty ("os.name", "").toLowerCase (Locale.ROOT);
        final String userHome = System.getProperty ("user.home");

        if (osName.contains ("win"))
        {
            final String appData = System.getenv ("APPDATA");
            if (appData != null && !appData.isEmpty ())
                return Paths.get (appData, "DrivenByMoss-SCALES");
            if (userHome != null && !userHome.isEmpty ())
                return Paths.get (userHome, "AppData", "Roaming", "DrivenByMoss-SCALES");
        }
        else if (osName.contains ("mac"))
        {
            if (userHome != null && !userHome.isEmpty ())
                return Paths.get (userHome, "Library", "Application Support", "DrivenByMoss-SCALES");
        }
        else
        {
            if (userHome != null && !userHome.isEmpty ())
                return Paths.get (userHome, ".config", "DrivenByMoss-SCALES");
        }

        return Paths.get ("DrivenByMoss-SCALES");
    }
}
