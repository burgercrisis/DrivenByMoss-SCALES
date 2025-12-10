package de.mossgrabers.framework.scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Custom user-defined 12-TET scale.
 */
public class CustomScale
{
    private String  id;
    private String  name;
    private int []  intervals;
    private String  description;


    public CustomScale ()
    {
        // Default constructor for JSON frameworks
    }


    public CustomScale (final String id, final String name, final int [] intervals, final String description)
    {
        this.id = id;
        this.name = name;
        this.intervals = intervals == null ? null : intervals.clone ();
        this.description = description;
    }


    public String getId ()
    {
        return this.id;
    }


    public void setId (final String id)
    {
        this.id = id;
    }


    public String getName ()
    {
        return this.name;
    }


    public void setName (final String name)
    {
        this.name = name;
    }


    public int [] getIntervals ()
    {
        return this.intervals == null ? null : this.intervals.clone ();
    }


    public void setIntervals (final int [] intervals)
    {
        this.intervals = intervals == null ? null : intervals.clone ();
    }


    public String getDescription ()
    {
        return this.description;
    }


    public void setDescription (final String description)
    {
        this.description = description;
    }


    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final CustomScale other = (CustomScale) obj;
        return Objects.equals (this.id, other.id) && Objects.equals (this.name, other.name) &&
            Arrays.equals (this.intervals, other.intervals) && Objects.equals (this.description, other.description);
    }


    @Override
    public int hashCode ()
    {
        int result = Objects.hash (this.id, this.name, this.description);
        result = 31 * result + Arrays.hashCode (this.intervals);
        return result;
    }


    public List<String> validate ()
    {
        return validate (this.name, this.intervals);
    }


    public static List<String> validate (final String name, final int [] intervals)
    {
        final List<String> errors = new ArrayList<> ();

        if (name == null || name.trim ().isEmpty ())
            errors.add ("Name must not be empty.");

        if (intervals == null || intervals.length < 2)
        {
            errors.add ("Intervals must contain at least two entries.");
            return errors;
        }

        boolean hasRoot = false;
        int previous = -1;
        for (final int value: intervals)
        {
            if (value < 0 || value > 11)
            {
                errors.add ("Intervals must be between 0 and 11.");
                break;
            }
            if (previous >= value)
            {
                errors.add ("Intervals must be strictly ascending without duplicates.");
                break;
            }
            if (value == 0)
                hasRoot = true;
            previous = value;
        }

        if (!hasRoot)
            errors.add ("Intervals must contain 0 as the root.");

        return errors.isEmpty () ? Collections.emptyList () : errors;
    }
}
