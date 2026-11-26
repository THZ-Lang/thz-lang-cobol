package thz.cobol.model;

import java.util.Collections;
import java.util.List;

/**
 * Representa um elemento visual ou campo de tela definido na SCREEN SECTION do COBOL.
 */
public record CobolScreenItem(
        String name,
        int level,
        Integer line,
        Integer column,
        String value,
        Integer foregroundColor,
        Integer backgroundColor,
        boolean highlight,
        boolean reverseVideo,
        boolean blink,
        boolean blankScreen,
        String fromField,
        String toField,
        String usingField,
        List<CobolScreenItem> children
) {
    public CobolScreenItem {
        children = children != null ? List.copyOf(children) : List.of();
    }

    public static Builder builder(String name, int level) {
        return new Builder(name, level);
    }

    public static final class Builder {
        private final String name;
        private final int level;
        private Integer line;
        private Integer column;
        private String value;
        private Integer foregroundColor;
        private Integer backgroundColor;
        private boolean highlight;
        private boolean reverseVideo;
        private boolean blink;
        private boolean blankScreen;
        private String fromField;
        private String toField;
        private String usingField;
        private List<CobolScreenItem> children = List.of();

        public Builder(String name, int level) {
            this.name = name;
            this.level = level;
        }

        public Builder line(Integer line) { this.line = line; return this; }
        public Builder column(Integer column) { this.column = column; return this; }
        public Builder value(String value) { this.value = value; return this; }
        public Builder foregroundColor(Integer fg) { this.foregroundColor = fg; return this; }
        public Builder backgroundColor(Integer bg) { this.backgroundColor = bg; return this; }
        public Builder highlight(boolean highlight) { this.highlight = highlight; return this; }
        public Builder reverseVideo(boolean reverseVideo) { this.reverseVideo = reverseVideo; return this; }
        public Builder blink(boolean blink) { this.blink = blink; return this; }
        public Builder blankScreen(boolean blankScreen) { this.blankScreen = blankScreen; return this; }
        public Builder fromField(String fromField) { this.fromField = fromField; return this; }
        public Builder toField(String toField) { this.toField = toField; return this; }
        public Builder usingField(String usingField) { this.usingField = usingField; return this; }
        public Builder children(List<CobolScreenItem> children) { this.children = children; return this; }

        public CobolScreenItem build() {
            return new CobolScreenItem(
                    name, level, line, column, value,
                    foregroundColor, backgroundColor,
                    highlight, reverseVideo, blink, blankScreen,
                    fromField, toField, usingField, children
            );
        }
    }
}
