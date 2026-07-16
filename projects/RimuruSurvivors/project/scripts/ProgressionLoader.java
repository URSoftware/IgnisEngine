import com.ignis.core.AssetResolver;
import com.rimurusurvivors.domain.WeaponLevelStats;
import com.rimurusurvivors.domain.WeaponProgression;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Le project/data/rimuru-progression.json (copia aparada de
 * mods/vampire-survivors-rimuru/data/rimuru-progression.json) e monta a WeaponProgression
 * (com.rimurusurvivors.domain, empacotada em project/libs/rimuru-survivors-domain.jar).
 * Chamado uma vez e cacheado estaticamente — os scripts de gameplay pedem a instancia
 * em vez de reler o arquivo a cada tick.
 */
public final class ProgressionLoader {

    private static WeaponProgression cachedWeaponProgression;

    private ProgressionLoader() {
    }

    public static synchronized WeaponProgression weaponProgression() {
        if (cachedWeaponProgression == null) {
            cachedWeaponProgression = loadWeaponProgression();
        }
        return cachedWeaponProgression;
    }

    private static WeaponProgression loadWeaponProgression() {
        File file = AssetResolver.resolve("data/rimuru-progression.json");
        JSONObject root;
        try {
            String content = Files.readString(file.toPath());
            root = new JSONObject(content);
        } catch (IOException e) {
            throw new IllegalStateException("Nao consegui ler rimuru-progression.json em " + file, e);
        }

        JSONArray levelsJson = root.getJSONObject("weapon").getJSONArray("levels");
        List<WeaponLevelStats> levels = new ArrayList<>();
        for (int i = 0; i < levelsJson.length(); i++) {
            JSONObject levelJson = levelsJson.getJSONObject(i);
            levels.add(new WeaponLevelStats(
                    levelJson.getInt("level"),
                    levelJson.optDouble("damage", 0),
                    levelJson.optDouble("cooldown", 0),
                    levelJson.optInt("amount", 0),
                    levelJson.optDouble("area", 0),
                    levelJson.optDouble("speed", 0),
                    levelJson.optInt("pierce", 0),
                    levelJson.optDouble("slowSeconds", 0),
                    levelJson.optDouble("returnDamage", 0),
                    levelJson.optDouble("slowCap", 0),
                    levelJson.optDouble("damageMultiplier", 1.0),
                    levelJson.optString("summon", null)));
        }

        return new WeaponProgression(levels);
    }
}
