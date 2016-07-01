/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viewmodelgenerator;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author hamptos
 */
public class StringFactory {
    public static StringFactory begin() {
        return new StringFactory();
    }
    
    private final List<String> myLines = new LinkedList<>();
    
    public StringFactory line(String l) {
        myLines.add(l + "\n");
        
        return this;
    }
    
    public StringFactory block(StringFactory f) {
        for (String l : f.getLines()) {
            myLines.add("  " + l + "\n");
        }
        
        return this;
    }
    
    public Iterable<String> getLines() {
        return myLines;
    }
    
    public String done() {
        StringBuilder b = new StringBuilder();
        for (String l : myLines) {
            b.append(l);
        }
        
        return b.toString();
    }
}
