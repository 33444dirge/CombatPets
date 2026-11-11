package su.nightexpress.combatpets.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.data.impl.PetData;
import su.nightexpress.combatpets.data.impl.PetUser;
import su.nightexpress.combatpets.data.serialize.PetDataSerializer;
import su.nightexpress.nightcore.db.AbstractUserDataManager;
import su.nightexpress.nightcore.db.sql.column.Column;
import su.nightexpress.nightcore.db.sql.column.ColumnType;
import su.nightexpress.nightcore.db.sql.query.impl.SelectQuery;
import su.nightexpress.nightcore.db.sql.query.UserQueries;
import su.nightexpress.nightcore.db.sql.query.impl.UpdateQuery;
import su.nightexpress.nightcore.db.sql.query.type.ValuedQuery;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.function.Function;

public class DataHandler extends AbstractUserDataManager<PetsPlugin, PetUser> {

    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(PetData.class, new PetDataSerializer()).create();

    static final Column COLUMN_PETS = Column.of("pets", ColumnType.STRING);

    public DataHandler(@NotNull PetsPlugin plugin) {
        super(plugin);
    }

    @Override
    @NotNull
    protected Function<ResultSet, PetUser> createUserFunction() {
        return DataQueries.USER_LOAD;
    }

    @Override
    public void onSynchronize() {
        if (this.plugin.getPetManager() != null && this.plugin.getPetManager().isShuttingDown()) return;
        this.plugin.getUserManager().getLoaded().forEach(this::synchronizePets);
    }

    /*public void loadPets(@NotNull PetUser user) {
        var query = new SelectQuery<>(DataQueries.PETS_LOADER).column(COLUMN_PETS).where(COLUMN_USER_ID, WhereOperator.EQUAL, user.getId().toString());
        var pets = this.selectFirst(this.tableUsers, query);
        if (pets == null) return;

        user.load(pets);

        this.plugin.info("已加载玩家 " + user.getName() + " 的宠物数据。");
    }*/

    private void synchronizePets(@NotNull PetUser user) {
        if (this.plugin.getPetManager() != null && this.plugin.getPetManager().isShuttingDown()) return;
        if (user.isAutoSavePlanned() || !user.isAutoSyncReady()) return;

        PetUser fresh = this.getUser(user.getId());
        if (fresh == null) return;
        if (this.plugin.getPetManager() != null && this.plugin.getPetManager().isShuttingDown()) return;

        String activeId = null;
        Player player = user.getPlayer();
        if (player != null) {
            ActivePet pet = this.plugin.getPetManager().getPlayerPet(player);
            if (pet != null) activeId = pet.getTemplate().getId();
        }

        for (PetData petData : fresh.getPets().values()) {
            if (petData.getTemplate().getId().equalsIgnoreCase(activeId)) continue;

            user.putPet(petData);
        }
    }

    @Override
    @NotNull
    protected GsonBuilder registerAdapters(@NotNull GsonBuilder builder) {
        return builder;
    }

    @Override
    protected void addTableColumns(@NotNull List<Column> columns) {
        columns.add(COLUMN_PETS);
    }

    @Override
    protected void addUpsertQueryData(@NotNull ValuedQuery<?, PetUser> query) {
        query.setValue(COLUMN_PETS, user -> GSON.toJson(user.getPetsSnapshot()));
    }

    /** Keeps the final lifecycle flush ordered after any in-flight autosave. */
    @Override
    public synchronized void saveUsersFully(@NotNull Collection<PetUser> users) {
        try {
            this.saveUsersForShutdown(users);
        }
        catch (SQLException exception) {
            // NightCore will still clear its flag; PetUser retains the unconfirmed version for retry.
            this.plugin.getLogger().log(Level.SEVERE, "完整保存宠物数据失败，未确认的保存请求将保留并重试。", exception);
        }
    }

    /** Final unload flush: use the existing schema/bindings, but never swallow JDBC failures. */
    public synchronized void saveUsersForShutdown(@NotNull Collection<PetUser> users) throws SQLException {
        if (users.isEmpty()) return;
        UpdateQuery<PetUser> query = UserQueries.updateCommons();
        this.addUpsertQueryData(query);
        Map<PetUser, Long> versions = new LinkedHashMap<>();
        users.forEach(user -> versions.put(user, user.getSaveVersion()));
        try (Connection connection = this.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(query.createSQL(this.tableUsers))) {
                statement.setQueryTimeout(15);
                int batchSize = 0;
                for (PetUser user : versions.keySet()) {
                    query.onExecute(statement, user);
                    statement.addBatch();
                    if (++batchSize == 500) {
                        checkSaveBatch(statement.executeBatch());
                        statement.clearBatch();
                        batchSize = 0;
                    }
                }
                if (batchSize != 0) checkSaveBatch(statement.executeBatch());
                connection.commit();
                versions.forEach(PetUser::acknowledgeSave);
            }
            catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw exception;
            }
        }
    }

    private static void checkSaveBatch(int[] results) throws SQLException {
        for (int result : results) {
            if (result == Statement.EXECUTE_FAILED) throw new SQLException("宠物数据批量保存失败。");
        }
    }

    @Override
    protected void addSelectQueryData(@NotNull SelectQuery<PetUser> query) {
        query.column(COLUMN_PETS);
    }
}
