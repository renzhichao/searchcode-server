package com.searchcode.app.service.index;

import com.searchcode.app.dao.LanguageType;
import com.searchcode.app.dao.MySQLRepo;
import com.searchcode.app.dao.Source;
import com.searchcode.app.dto.CodeFacetLanguage;
import com.searchcode.app.dto.CodeIndexDocument;
import com.searchcode.app.service.CacheSingleton;
import com.searchcode.app.service.Singleton;
import junit.framework.TestCase;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SphinxIndexServiceTest extends TestCase {
    public void testSearch() {
        if (Singleton.getHelpers().isStandaloneInstance()) return;

        var sphinxIndexService = new SphinxIndexService();

        var someList = new ArrayList<String>();

        for (int i = 0; i < 100; i++) {
            someList.add("" + i);
        }
        var res = sphinxIndexService.search("test", null, 0, false);

        someList.parallelStream()
                .forEach(x -> sphinxIndexService.search("test", null, 0, false));
    }

    public void testSearchEnsureConnectionsClose() {
        if (Singleton.getHelpers().isStandaloneInstance()) return;

        var sphinxIndexService = new SphinxIndexService();

        for (int i = 0; i < 100; i++) {
            sphinxIndexService.search("test", null, 0, false);
        }
    }

    private CodeIndexDocument codeIndexDocument = new CodeIndexDocument()
            .setRepoLocationRepoNameLocationFilename("repoLocationRepoNameLocationFilename")
            .setRepoName("this is a repositoryname")
            .setFileName("fileName")
            .setFileLocation("fileLocation")
            .setFileLocationFilename("fileLocationFilename")
            .setMd5hash("md5hash")
            .setLanguageName("language name")
            .setCodeLines(100)
            .setContents("this is some content to search on test")
            .setRepoRemoteLocation("repoRemoteLocation")
            .setCodeOwner("owner")
            .setDisplayLocation("mydisplaylocation")
            .setSource("source");


    public void testTransformLanguageTypeEmpty() {
        if (Singleton.getHelpers().isStandaloneInstance()) return;

        var mock = Mockito.mock(LanguageType.class);
        var mockRepo = Mockito.mock(MySQLRepo.class);
        var mockSource = Mockito.mock(Source.class);
        var sphinxIndexService = new SphinxIndexService(mock, mockRepo, mockSource, CacheSingleton.getProjectStatsCache());

        List<CodeFacetLanguage> codeFacetLanguages = sphinxIndexService.transformLanguageType(new ArrayList<>());
        assertThat(codeFacetLanguages).hasSize(0);
    }
}
