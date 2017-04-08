function mce_check_env
{

if [ -z "$MCE_SERVICE_API" ] 
then
   echo "MCE environment not set. Please use source setenv.sh"
   exit 1
fi    
}

function dbg_print_cmd
{

if [ "$MCE_DEBUG" ] 
then
   eval "echo $MCE_SERVICE_API" 
fi    
}

mce_check_env
dbg_print_cmd
eval $MCE_SERVICE_API

