package info.archinnov.achilles.internal.metadata.holder;

import static info.archinnov.achilles.type.Options.LWTPredicate.LWTType.EQUAL_CONDITION;
import static java.util.Arrays.asList;
import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.archinnov.achilles.type.IndexCondition;
import info.archinnov.achilles.type.Options.LWTCondition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EntityMetaTranscoderTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private EntityMeta meta;

    private EntityMetaTranscoder view;

    @Before
    public void setUp() {
        view = new EntityMetaTranscoder(meta);
    }

    @Test
    public void should_encode_LWT_condition_value() throws Exception {
        //Given
        PropertyMeta nameMeta = mock(PropertyMeta.class, RETURNS_DEEP_STUBS);
        LWTCondition LWTCondition = spy(new LWTCondition(EQUAL_CONDITION,"name", "DuyHai"));

        when(meta.getAllMetasExceptCounters()).thenReturn(asList(nameMeta));
        when(nameMeta.getCQLColumnName()).thenReturn("name");
        when(nameMeta.getPropertyName()).thenReturn("name");
        when(nameMeta.forTranscoding().encodeToCassandra("DuyHai")).thenReturn("DuyHai");

        //When
        final Object encoded = view.encodeCasConditionValue(LWTCondition);

        //Then
        assertThat(encoded).isEqualTo("DuyHai");
        verify(LWTCondition).encodedValue("DuyHai");
    }

    @Test
    public void should_encode_index_condition_value() throws Exception {
        //Given
        PropertyMeta nameMeta = mock(PropertyMeta.class, RETURNS_DEEP_STUBS);
        IndexCondition indexCondition = spy(new IndexCondition("name", "DuyHai"));

        when(meta.getAllMetasExceptCounters()).thenReturn(asList(nameMeta));
        when(nameMeta.getCQLColumnName()).thenReturn("name");
        when(nameMeta.getPropertyName()).thenReturn("name");
        when(nameMeta.forTranscoding().encodeToCassandra("DuyHai")).thenReturn("DuyHai");

        //When
        final Object encoded = view.encodeIndexConditionValue(indexCondition);

        //Then
        assertThat(encoded).isEqualTo("DuyHai");
        verify(indexCondition).encodedValue("DuyHai");
    }
}