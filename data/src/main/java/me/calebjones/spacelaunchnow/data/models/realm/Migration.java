package me.calebjones.spacelaunchnow.data.models.realm;

import java.util.Calendar;
import java.util.Date;

import io.realm.DynamicRealm;
import io.realm.DynamicRealmObject;
import io.realm.FieldAttribute;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;
import me.calebjones.spacelaunchnow.data.models.Constants;
import me.calebjones.spacelaunchnow.data.models.news.Article;
import timber.log.Timber;

public class Migration implements RealmMigration {

    @Override
    public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {
        Timber.d("Migrate - From %s to %s", oldVersion, newVersion);

        // Access the Realm schema in order to create, modify or delete classes and their fields.
        RealmSchema schema = realm.getSchema();

        Timber.i("Current Schema - Version %s", oldVersion);
        for (RealmObjectSchema objectSchema : schema.getAll()) {
            Timber.d("Name: %s Fields: %s", objectSchema.getClassName(), objectSchema.getFieldNames());
            for (String field : objectSchema.getFieldNames()){
                Timber.v("Field %s Type %s", field, objectSchema.getFieldType(field));
            }
        }
        /*
         Migration for Version 1.6.0
         Releases prior to 1.6.0 do not need to be migrated.
        */
        if (oldVersion <= Constants.DB_SCHEMA_VERSION_1_5_5) {
            // Change type from String to int
                schema.get("Launch")
                        .addField("locationid", Integer.class)
                        .transform(new RealmObjectSchema.Function() {
                            @Override
                            public void apply(DynamicRealmObject obj) {
                                if (obj.getObject("location") != null) {
                                    obj.set("locationid", obj.getObject("location").get("id"));
                                    Timber.v("Adding Locationg ID: %s for %s",
                                             obj.getObject("location").get("id"),
                                             obj.getObject("location").get("name"));
                                } else {
                                    obj.set("locationid", 0);
                                    Timber.v("Unable to find location id, setting to default 0");
                                }
                            }
                        });
            oldVersion++;
        }


        if (oldVersion <= Constants.DB_SCHEMA_VERSION_1_8_0) {
            RealmObjectSchema LSP = schema.get("LSP");
            if (LSP == null) {
                RealmObjectSchema lsp = schema.create("LSP")
                        .addField("id", Integer.class, FieldAttribute.PRIMARY_KEY)
                        .addField("name", String.class)
                        .addField("abbrev", String.class)
                        .addField("countryCode", String.class)
                        .addField("type", Integer.class)
                        .addField("infoURL", String.class)
                        .addField("wikiURL", String.class);

                if (!schema.get("Launch").hasField("lsp")) {
                    schema.get("Launch")
                            .addRealmObjectField("lsp", lsp);
                }
            }

            oldVersion++;
        }

        if (oldVersion <= Constants.DB_SCHEMA_VERSION_1_8_1) {
            if (schema.get("RocketDetails") != null){
                schema.remove("RocketDetails");
                RealmObjectSchema details = schema.create("RocketDetail");

                if (details != null){
                    Timber.i("Migrating RocketDetail to new schema.");
                    details.addField("id", int.class, FieldAttribute.PRIMARY_KEY)
                            .addField("name", String.class)
                            .addField("infoURL", String.class)
                            .addField("wikiURL", String.class)
                            .addField("imageURL", String.class)
                            .addField("description", String.class)
                            .addField("alias", String.class)
                            .addField("variant", String.class)
                            .addField("family", String.class)
                            .addField("sFamily", String.class)
                            .addField("manufacturer", String.class)
                            .addField("length", String.class)
                            .addField("diameter", String.class)
                            .addField("launchMass", String.class)
                            .addField("leoCapacity", String.class)
                            .addField("gtoCapacity", String.class)
                            .addField("thrust", String.class)
                            .addField("vehicleClass", String.class)
                            .addField("apogee", String.class)
                            .addField("range", String.class)
                            .addField("maxStage", Integer.class)
                            .addField("minStage", Integer.class);
                }
            }
            oldVersion++;
        }

        if (oldVersion <= Constants.DB_SCHEMA_VERSION_1_8_2) {
            RealmObjectSchema details = schema.get("RocketDetail");
            if (details != null ){
                details.renameField("manufacturer", "agency");
            }

            RealmObjectSchema launcherAgency = schema.create("LauncherAgency");
            launcherAgency.addField("agency", String.class, FieldAttribute.PRIMARY_KEY)
                    .addField("launchers", String.class)
                    .addField("orbiters", String.class)
                    .addField("description", String.class)
                    .addField("imageURL", String.class)
                    .addField("nationURL", String.class);
            oldVersion++;
        }

        if (oldVersion < Constants.DB_SCHEMA_VERSION_2_0_0) {
            RealmObjectSchema details = schema.get("RocketDetail");
            if (details != null){
                details.addField("fullName", String.class);
            }
            RealmObjectSchema launch = schema.get("Launch");
            if (launch != null){
                launch.addField("tbddate", Integer.class);
            }
            oldVersion++;
        }

        if (oldVersion < Constants.DB_SCHEMA_VERSION_2_3_0) {
            RealmObjectSchema article = schema.create("Article");
            article.addField("title", String.class, FieldAttribute.PRIMARY_KEY);
            article.addField("link", String.class);
            article.addField("description", String.class);
            article.addField("guid", String.class);
            article.addField("pubDate", String.class);
            article.addField("content", String.class);
            article.addField("mediaUrl", String.class);

            RealmObjectSchema channel = schema.create("Channel");
            channel.addField("title", String.class);
            channel.addRealmListField("articles", article);

            RealmObjectSchema newsFeed = schema.create("NewsFeedResponse");
            newsFeed.addField("version", String.class);
            newsFeed.addField("lastUpdate", long.class, FieldAttribute.PRIMARY_KEY);
            newsFeed.addRealmObjectField("channel", channel);
            oldVersion++;
        }

        if (oldVersion < Constants.DB_SCHEMA_VERSION_2_3_1){
            RealmObjectSchema article = schema.get("Article");
            if (!article.getFieldNames().contains("date")) {
                article.addField("date", Date.class, FieldAttribute.INDEXED);
            }
            oldVersion = Constants.DB_SCHEMA_VERSION_2_3_1;
        }

        if (oldVersion < Constants.DB_SCHEMA_VERSION_2_3_2){
            if (schema.get("LauncherAgency") != null) {
                schema.remove("LauncherAgency");
            }
            RealmObjectSchema agency = schema.create("SLNAgency");
            agency.addField("id", int.class, FieldAttribute.PRIMARY_KEY)
                    .addField("name", String.class)
                    .addField("launchers", String.class)
                    .addField("orbiters", String.class)
                    .addField("description", String.class)
                    .addField("imageURL", String.class)
                    .addField("nationURL", String.class)
                    .addField("CEO", String.class)
                    .addField("foundingYear", String.class)
                    .addField("launchLibraryURL", String.class)
                    .addField("launchLibraryId", int.class);
            oldVersion = Constants.DB_SCHEMA_VERSION_2_3_2;
        }

        if (oldVersion < Constants.DB_SCHEMA_VERSION_2_6_0){
            RealmObjectSchema launch = schema.get("Launch");
            launch.addField("lastUpdate", Date.class);
            launch.transform(new RealmObjectSchema.Function() {
                @Override
                public void apply(DynamicRealmObject obj) {
                    obj.set("lastUpdate", Calendar.getInstance().getTime());
                }
            });
            oldVersion = Constants.DB_SCHEMA_VERSION_2_6_0;
        }


        Timber.i("Final Schema - Version %s", oldVersion);
        for (RealmObjectSchema objectSchema : schema.getAll()) {
            Timber.d("Name: %s Fields: %s", objectSchema.getClassName(), objectSchema.getFieldNames());
            for (String field : objectSchema.getFieldNames()){
                Timber.v("Field %s Type %s", field, objectSchema.getFieldType(field));
            }
        }
    }
}
