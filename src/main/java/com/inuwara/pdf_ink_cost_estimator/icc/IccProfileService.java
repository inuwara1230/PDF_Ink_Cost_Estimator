package com.inuwara.pdf_ink_cost_estimator.icc;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Built-in profiles live inside the jar (src/main/resources/profiles).
 * Profiles added by a shop are stored in %APPDATA%\PDFCostEstimator\icc-profiles,
 * so they survive upgrades, uninstalls and trial-to-full installs.
 */
@Service
public class IccProfileService {

    public static final String DEFAULT_PROFILE = "USWebUncoated.icc";

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    // Letters, digits, space . _ ( ) - ; must end in .icc or .icm; no slashes, so no folder tricks
    private static final Pattern SAFE_NAME =
            Pattern.compile("^[A-Za-z0-9_(][A-Za-z0-9 ._()\\-]{0,100}\\.(icc|icm)$", Pattern.CASE_INSENSITIVE);

    private final Path userDir;
    private volatile List<String> bundled;

    public IccProfileService() {
        String base = System.getenv("APPDATA");
        this.userDir = Paths.get(base != null ? base : System.getProperty("user.home"),
                "PDFCostEstimator", "icc-profiles").toAbsolutePath().normalize();
    }

    // ---------- listing ----------

    public List<String> bundledNames() {
        List<String> cached = bundled;
        if (cached == null) {
            List<String> names = new ArrayList<>();
            try {
                for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:profiles/*")) {
                    String f = r.getFilename();
                    if (f != null && SAFE_NAME.matcher(f).matches()) names.add(f);
                }
            } catch (IOException e) {
                System.err.println("Could not list built-in ICC profiles: " + e.getMessage());
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            bundled = cached = names;
        }
        return cached;
    }

    public List<String> customNames() {
        if (!Files.isDirectory(userDir)) return List.of();
        try (Stream<Path> s = Files.list(userDir)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> SAFE_NAME.matcher(n).matches())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<String> allNames() {
        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(bundledNames());
        all.addAll(customNames());
        return new ArrayList<>(all);
    }

    // ---------- loading (used by the cost calculation) ----------

    public ICC_Profile load(String requested) throws IOException {
        String name = (requested == null || requested.isBlank()) ? DEFAULT_PROFILE : requested.trim();
        if (!SAFE_NAME.matcher(name).matches()) throw new IOException("Invalid profile name: " + name);

        Path custom = resolveInUserDir(name);
        if (custom != null && Files.isRegularFile(custom)) {
            return ICC_Profile.getInstance(Files.readAllBytes(custom));
        }
        for (String b : bundledNames()) {
            if (b.equals(name)) {
                try (InputStream is = new ClassPathResource("profiles/" + b).getInputStream()) {
                    return ICC_Profile.getInstance(is);
                }
            }
        }
        throw new IOException("Profile not found: " + name);
    }

    // ---------- adding ----------

    /** Validates and stores a profile. Throws IllegalArgumentException with a message fit to show the shop owner. */
    public String save(String originalName, byte[] data) throws IOException {
        String name = cleanName(originalName);

        if (data == null || data.length == 0) throw new IllegalArgumentException("The file is empty.");
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("The file is too large for a colour profile (limit 10 MB).");

        for (String b : bundledNames()) {
            if (b.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("A built-in profile already uses the name \"" + name + "\". Rename the file and try again.");
            }
        }

        validate(data);

        Path target = resolveInUserDir(name);
        if (target == null) throw new IllegalArgumentException("Invalid file name.");

        Files.createDirectories(userDir);
        Path tmp = userDir.resolve(name + ".tmp");        // write, then replace: a power cut can't leave a half file
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    // ---------- removing ----------

    /** Removes a profile the shop added. Built-in profiles can't be removed. */
    public boolean delete(String name) throws IOException {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return false;
        Path target = resolveInUserDir(name);
        return target != null && Files.deleteIfExists(target);
    }

    // ---------- helpers ----------

    static String cleanName(String original) {
        if (original == null) throw new IllegalArgumentException("The file has no name.");
        String n = original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        n = n.replaceAll("[^A-Za-z0-9 ._()\\-]", "_");
        if (!SAFE_NAME.matcher(n).matches()) {
            throw new IllegalArgumentException("Please choose a file ending in .icc or .icm (name up to 100 characters).");
        }
        return n;
    }

    private Path resolveInUserDir(String name) {
        Path p = userDir.resolve(name).normalize();
        return (p.getParent() != null && p.getParent().equals(userDir)) ? p : null;
    }

    /** The cost calculation reads four ink channels, so only CMYK profiles that really convert are accepted. */
    private static void validate(byte[] data) {
        ICC_Profile profile;
        try {
            profile = ICC_Profile.getInstance(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("This is not a valid ICC colour profile.");
        }
        if (profile.getColorSpaceType() != ColorSpace.TYPE_CMYK || profile.getNumComponents() != 4) {
            throw new IllegalArgumentException("Only CMYK profiles can be used here. This one is for a different colour mode.");
        }
        try {
            ColorConvertOp op = new ColorConvertOp(new ICC_ColorSpace(profile), null);
            BufferedImage probe = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            BufferedImage out = op.filter(probe, null);
            out.getRaster().getPixel(0, 0, new int[4]);
        } catch (Exception e) {
            throw new IllegalArgumentException("This profile could not be used for colour conversion.");
        }
    }
}