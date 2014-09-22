package org.klomp.cassowary.layout.demo;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.klomp.cassowary.layout.Attribute;
import org.klomp.cassowary.layout.CassowaryLayout;
import org.klomp.cassowary.layout.LayoutConstraint;
import org.klomp.cassowary.layout.Relation;

public class LayoutDemo extends JFrame {

    public LayoutDemo() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("LayoutDemo");
        JPanel panel = new JPanel();
        getContentPane().add(panel);
        CassowaryLayout layout = new CassowaryLayout();
        panel.setLayout(layout);

        JLabel l1 = new JLabel("Label1");
        JLabel l2 = new JLabel("Label2");
        JLabel l3 = new JLabel("Label3");
        JLabel l4 = new JLabel("Label4");

        panel.add(l1);
        panel.add(l2);
        panel.add(l3);
        panel.add(l4);

        LayoutConstraint c1 = new LayoutConstraint(l1, Attribute.RIGHT, Relation.EQ, l2, Attribute.LEFT, -20);
        LayoutConstraint c2 = new LayoutConstraint(l1, Attribute.TOP, Relation.EQ, l2, Attribute.TOP);
        LayoutConstraint c3 = new LayoutConstraint(l1, Attribute.BOTTOM, Relation.EQ, l3, Attribute.TOP, -20);
        LayoutConstraint c4 = new LayoutConstraint(l1, Attribute.LEFT, Relation.EQ, l3, Attribute.LEFT);
        LayoutConstraint c5 = new LayoutConstraint(l2, Attribute.BOTTOM, Relation.EQ, l4, Attribute.TOP, -20);
        LayoutConstraint c6 = new LayoutConstraint(l2, Attribute.LEFT, Relation.EQ, l4, Attribute.LEFT);

        layout.addConstraint(c1);
        layout.addConstraint(c2);
        layout.addConstraint(c3);
        layout.addConstraint(c4);
        layout.addConstraint(c5);
        layout.addConstraint(c6);
    }

    public static void main(String[] args) {
        LayoutDemo demo = new LayoutDemo();
        demo.setSize(500, 500);
        demo.setVisible(true);
    }
}
