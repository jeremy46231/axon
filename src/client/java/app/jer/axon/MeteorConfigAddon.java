package app.jer.axon;

import app.jer.axon.service.MeteorService;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;

public class MeteorConfigAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        MeteorService.initialize();
        Utils.runLater(1000, MeteorService::initialize);
    }

    @Override
    public String getPackage() {
        return "app.jer.axon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("jeremy46231", "axon");
    }

    @Override
    public String getWebsite() {
        return "https://axon.jer.app";
    }
}
